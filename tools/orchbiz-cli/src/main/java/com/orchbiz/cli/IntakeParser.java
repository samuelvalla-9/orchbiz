package com.orchbiz.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts a questionnaire export (.csv / .xlsx) into the canonical intake JSON defined by intake.schema.json. */
public final class IntakeParser {

    private static final Map<String, String> HEADER_ALIASES = Map.of("timestamp", "submission_date");
    private static final Map<String, String> CURRENCY_SYMBOLS = Map.of("₹", "INR", "$", "USD", "€", "EUR", "£", "GBP");
    private static final Pattern CURRENCY_CODE = Pattern.compile("\\b([A-Z]{3})\\b");
    private static final Pattern AMOUNT = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)");

    /** Free-text budget column -> [amount field, currency field]. */
    static final Map<String, String[]> BUDGET_FIELDS = new LinkedHashMap<>();

    static {
        BUDGET_FIELDS.put("setup_budget_limit", new String[]{"setup_budget_amount", "setup_budget_currency"});
        BUDGET_FIELDS.put("monthly_ops_budget", new String[]{"monthly_ops_budget_amount", "monthly_ops_budget_currency"});
    }

    public record Result(ObjectNode intake, List<String> notes) { }

    private final JsonNode schemaProperties;

    public IntakeParser(JsonNode intakeSchema) {
        this.schemaProperties = intakeSchema.path("properties");
    }

    /**
     * Parses {@code file} and selects the row whose client_slug equals {@code slug}; if no row carries a slug and the
     * file holds exactly one row, that row is used.
     */
    public Result parse(Path file, String slug) {
        List<Map<String, String>> rows = readRows(file);
        if (rows.isEmpty()) {
            throw new CliException("No data rows found in " + file);
        }
        Map<String, String> row = selectRow(rows, slug, file);
        return normalise(row);
    }

    Result normalise(Map<String, String> row) {
        List<String> notes = new ArrayList<>();
        ObjectNode out = Workspace.JSON.createObjectNode();
        for (Map.Entry<String, String> e : row.entrySet()) {
            String key = e.getKey();
            String raw = e.getValue() == null ? "" : e.getValue().trim();
            if (raw.isEmpty()) {
                continue;
            }
            String type = schemaProperties.path(key).path("type").asText("string");
            switch (type) {
                case "array" -> {
                    ArrayNode arr = out.putArray(key);
                    splitList(raw).forEach(arr::add);
                }
                case "boolean" -> {
                    Boolean b = parseBoolean(raw);
                    if (b == null) {
                        out.put(key, raw);
                        notes.add("Could not interpret '" + raw + "' as yes/no for " + key);
                    } else {
                        out.put(key, b);
                    }
                }
                case "number", "integer" -> {
                    BigDecimal n = parseAmount(raw);
                    if (n == null) {
                        out.put(key, raw);
                        notes.add("Could not interpret '" + raw + "' as a number for " + key);
                    } else {
                        out.put(key, n);
                    }
                }
                default -> out.put(key, raw);
            }
        }
        deriveBudgets(out, notes);
        return new Result(out, notes);
    }

    private void deriveBudgets(ObjectNode out, List<String> notes) {
        for (Map.Entry<String, String[]> e : BUDGET_FIELDS.entrySet()) {
            String text = out.path(e.getKey()).asText("");
            String amountField = e.getValue()[0];
            String currencyField = e.getValue()[1];
            if (text.isEmpty()) {
                continue;
            }
            if (!out.has(amountField)) {
                BigDecimal amount = parseAmount(text);
                if (amount != null) {
                    out.put(amountField, amount);
                    notes.add("Derived " + amountField + "=" + amount.toPlainString() + " from " + e.getKey() + " '" + text + "'");
                }
            }
            if (!out.has(currencyField)) {
                String currency = parseCurrency(text);
                if (currency != null) {
                    out.put(currencyField, currency);
                    notes.add("Derived " + currencyField + "=" + currency + " from " + e.getKey() + " '" + text + "'");
                }
            }
        }
    }

    static String parseCurrency(String text) {
        Matcher m = CURRENCY_CODE.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        for (Map.Entry<String, String> s : CURRENCY_SYMBOLS.entrySet()) {
            if (text.contains(s.getKey())) {
                return s.getValue();
            }
        }
        return null;
    }

    static BigDecimal parseAmount(String text) {
        Matcher m = AMOUNT.matcher(text);
        if (!m.find()) {
            return null;
        }
        return new BigDecimal(m.group(1).replace(",", ""));
    }

    static Boolean parseBoolean(String raw) {
        String v = raw.toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("yes") || v.equals("y") || v.equals("1") || v.startsWith("yes,") || v.startsWith("i consent")) {
            return Boolean.TRUE;
        }
        if (v.equals("false") || v.equals("no") || v.equals("n") || v.equals("0") || v.startsWith("no,")) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** Splits on commas/newlines that are not inside parentheses, e.g. "Email, WhatsApp (Approved, Template)". */
    static List<String> splitList(String raw) {
        List<String> items = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (char c : raw.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')' && depth > 0) depth--;
            if ((c == ',' || c == ';' || c == '\n') && depth == 0) {
                addItem(items, cur);
            } else {
                cur.append(c);
            }
        }
        addItem(items, cur);
        return items;
    }

    private static void addItem(List<String> items, StringBuilder cur) {
        String s = cur.toString().trim();
        if (!s.isEmpty()) {
            items.add(s);
        }
        cur.setLength(0);
    }

    static String normaliseHeader(String header) {
        String h = header.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return HEADER_ALIASES.getOrDefault(h, h);
    }

    private static Map<String, String> selectRow(List<Map<String, String>> rows, String slug, Path file) {
        List<Map<String, String>> matches = rows.stream()
                .filter(r -> slug != null && slug.equals(r.getOrDefault("client_slug", "").trim()))
                .toList();
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            throw new CliException(matches.size() + " rows in " + file + " have client_slug '" + slug
                    + "'. Keep only the final response in the file.");
        }
        if (rows.size() == 1) {
            return rows.get(0);
        }
        throw new CliException(rows.size() + " rows in " + file + " and none has client_slug '" + slug
                + "'. Set client_slug on the intended response or remove the others.");
    }

    static List<Map<String, String>> readRows(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv")) {
                return readCsv(file);
            }
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return readXlsx(file);
            }
        } catch (IOException e) {
            throw new CliException("Could not read " + file + ": " + e.getMessage());
        }
        throw new CliException("Unsupported intake file type: " + file + " (expected .csv or .xlsx)");
    }

    private static List<Map<String, String>> readCsv(Path file) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true)
                .setTrim(true).build();
        List<Map<String, String>> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            for (CSVRecord rec : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(normaliseHeader(headers.get(i)), i < rec.size() ? rec.get(i) : "");
                }
                if (row.values().stream().anyMatch(v -> !v.isBlank())) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static List<Map<String, String>> readXlsx(Path file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        DataFormatter fmt = new DataFormatter(Locale.ROOT);
        try (InputStream in = Files.newInputStream(file); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> it = sheet.rowIterator();
            if (!it.hasNext()) {
                return rows;
            }
            Row headerRow = it.next();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell c = headerRow.getCell(i);
                headers.add(c == null ? "" : normaliseHeader(fmt.formatCellValue(c)));
            }
            while (it.hasNext()) {
                Row r = it.next();
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    if (headers.get(i).isEmpty()) continue;
                    row.put(headers.get(i), cellText(r.getCell(i), fmt));
                }
                if (row.values().stream().anyMatch(v -> !v.isBlank())) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static String cellText(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dt = cell.getLocalDateTimeCellValue();
            return dt.toLocalTime().toSecondOfDay() == 0 ? dt.toLocalDate().toString() : dt.toString();
        }
        if (type == CellType.BOOLEAN) {
            return Boolean.toString(cell.getBooleanCellValue());
        }
        return fmt.formatCellValue(cell);
    }
}
