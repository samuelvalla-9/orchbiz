package com.orchbiz.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchbizCliTest {

    private static final Path REPO = Workspace.locate(Path.of("")).root();
    private static final String SLUG = "apex-coaching";

    @TempDir
    Path tmp;
    private String lastOut;

    @BeforeEach
    void copyWorkspace() throws IOException {
        for (String dir : List.of("intake", "catalogue", "schemas", "clients/_template")) {
            copyTree(REPO.resolve(dir), tmp.resolve(dir));
        }
    }

    private int run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        List<String> all = new ArrayList<>(List.of(args));
        all.add("--root");
        all.add(tmp.toString());
        int code = OrchbizCli.commandLine(new PrintWriter(out, true), new PrintWriter(err, true)).execute(all.toArray(String[]::new));
        lastOut = out + err.toString();
        return code;
    }

    private void initWithSample() throws IOException {
        assertEquals(0, run("init-client", SLUG, "--business-name", "Apex Horizon Coaching Institute"), lastOut);
        Files.copy(tmp.resolve("intake/questionnaire-template.csv"), tmp.resolve("clients/" + SLUG + "/intake/response.csv"));
    }

    private Path state() {
        return tmp.resolve("clients/" + SLUG + "/state.json");
    }

    @Test
    void initClientCreatesValidStateAndRefusesOverwrite() throws IOException {
        initWithSample();
        assertEquals(0, run("validate", "state", state().toString()), lastOut);
        assertEquals(2, run("init-client", SLUG));
        assertTrue(lastOut.contains("refusing to overwrite"), lastOut);
        assertEquals(2, run("init-client", "Bad_Slug"));
    }

    @Test
    void parsesSampleIntakeAndDerivesTypes() throws IOException {
        initWithSample();
        assertEquals(0, run("parse-intake", SLUG), lastOut);
        JsonNode intake = Workspace.readJson(tmp.resolve("clients/" + SLUG + "/intake/intake.validated.json"));
        assertTrue(intake.get("ai_data_consent").asBoolean());
        assertEquals(5, intake.get("website_modules").size());
        assertEquals(6, intake.get("automation_modules").size());
        assertEquals(7, intake.get("enquiry_fields").size());
        assertEquals(2500, intake.get("monthly_ops_budget_amount").asInt());
        assertEquals("INR", intake.get("monthly_ops_budget_currency").asText());
    }

    @Test
    void derivesBudgetFromLegacyFreeTextColumns() throws IOException {
        initWithSample();
        Path csv = tmp.resolve("clients/" + SLUG + "/intake/response.csv");
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        String header = lines.get(0).replace("setup_budget_amount,setup_budget_currency,monthly_ops_budget_amount,monthly_ops_budget_currency,", "");
        String row = lines.get(1).replace("45000,INR,2500,INR,", "");
        Files.write(csv, List.of(header, row), StandardCharsets.UTF_8);
        assertEquals(0, run("parse-intake", SLUG), lastOut);
        assertTrue(lastOut.contains("Derived monthly_ops_budget_amount=2500"), lastOut);
    }

    @Test
    void blocksWhenConsentIsNotGiven() throws IOException {
        initWithSample();
        Path csv = tmp.resolve("clients/" + SLUG + "/intake/response.csv");
        String content = Files.readString(csv, StandardCharsets.UTF_8).replace(",2026-11-15,true,", ",2026-11-15,false,");
        Files.writeString(csv, content, StandardCharsets.UTF_8);
        assertEquals(1, run("parse-intake", SLUG));
        assertTrue(lastOut.contains("BLOCKER ai_data_consent"), lastOut);
        assertFalse(Files.exists(tmp.resolve("clients/" + SLUG + "/intake/intake.validated.json")));
    }

    @Test
    void reportsMissingRequiredFields() throws IOException {
        initWithSample();
        Path csv = tmp.resolve("clients/" + SLUG + "/intake/response.csv");
        String content = Files.readString(csv, StandardCharsets.UTF_8).replace("admissions@apexhorizon.example.com", "");
        Files.writeString(csv, content, StandardCharsets.UTF_8);
        assertEquals(1, run("parse-intake", SLUG));
        assertTrue(lastOut.contains("notification_recipients"), lastOut);
    }

    @Test
    void catalogueIsValid() {
        assertEquals(0, run("validate-catalogue"), lastOut);
        assertTrue(lastOut.contains("14 module manifest(s)"), lastOut);
    }

    @Test
    void catalogueDetectsBrokenManifest() throws IOException {
        Path mf = tmp.resolve("catalogue/websites/enquiry-form/manifest.json");
        ObjectNode node = (ObjectNode) Workspace.readJson(mf);
        node.put("version", "1.0");
        node.putArray("dependencies").add("does-not-exist");
        Workspace.writeJson(mf, node);
        assertEquals(1, run("validate-catalogue"));
        assertTrue(lastOut.contains("does-not-exist"), lastOut);
    }

    @Test
    void scopeAndBudgetForSample() throws IOException {
        initWithSample();
        assertEquals(0, run("parse-intake", SLUG), lastOut);
        assertEquals(0, run("check-scope", SLUG), lastOut);
        assertEquals(0, run("check-budget", SLUG, "--json"), lastOut);
        assertTrue(lastOut.contains("\"totalMonthlyCostUSD\" : 24"), lastOut);
        assertTrue(lastOut.contains("\"withinBudget\" : true"), lastOut);
    }

    @Test
    void budgetFailsWhenTooLow() throws IOException {
        initWithSample();
        assertEquals(0, run("parse-intake", SLUG), lastOut);
        Path intakeFile = tmp.resolve("clients/" + SLUG + "/intake/intake.validated.json");
        ObjectNode intake = (ObjectNode) Workspace.readJson(intakeFile);
        intake.put("monthly_ops_budget_amount", 500);
        Workspace.writeJson(intakeFile, intake);
        assertEquals(1, run("check-budget", SLUG));
        assertTrue(lastOut.contains("exceeds client budget"), lastOut);
    }

    @Test
    void addOnModuleIsWarnedNotFailed() throws IOException {
        initWithSample();
        assertEquals(0, run("parse-intake", SLUG), lastOut);
        Path intakeFile = tmp.resolve("clients/" + SLUG + "/intake/intake.validated.json");
        ObjectNode intake = (ObjectNode) Workspace.readJson(intakeFile);
        ((com.fasterxml.jackson.databind.node.ArrayNode) intake.get("automation_modules")).add("weekly-performance-report");
        Workspace.writeJson(intakeFile, intake);
        assertEquals(0, run("check-scope", SLUG), lastOut);
        assertTrue(lastOut.contains("ADD-ON automation/weekly-performance-report"), lastOut);
    }

    @Test
    void gatesRequireRecordedHumanApproval() throws IOException {
        initWithSample();
        assertEquals(1, run("check-gate", SLUG, "gate1"));

        ObjectNode s = (ObjectNode) Workspace.readJson(state());
        ObjectNode g1 = (ObjectNode) s.get("approvals").get("gate1_scope_and_architecture");
        g1.put("approved", true);
        Workspace.writeJson(state(), s);
        assertEquals(1, run("check-gate", SLUG, "gate1"));
        assertTrue(lastOut.contains("approvedBy"), lastOut);
        assertEquals(1, run("validate", "state", state().toString()), lastOut);

        g1.put("approvedBy", "operator");
        g1.put("approvedAt", "2026-10-29T18:00:00+05:30");
        Workspace.writeJson(state(), s);
        assertEquals(0, run("check-gate", SLUG, "gate1"), lastOut);
        assertEquals(1, run("check-gate", SLUG, "gate2"));
        assertEquals(2, run("check-gate", SLUG, "gate3"));
    }

    @Test
    void secretScannerFindsSecretsAndIgnoresPlaceholders() throws IOException {
        Path dir = Files.createDirectories(tmp.resolve("scan"));
        Files.writeString(dir.resolve("bad.json"), "{ \"key\": \"" + "AKIA" + "ABCDEFGHIJKLMNOP" + "\",\n"
                + "  \"pass" + "word\": \"hunter2hunter2\" }\n", StandardCharsets.UTF_8);
        assertEquals(1, run("scan-secrets", dir.toString()));
        assertTrue(lastOut.contains("aws-access-key"), lastOut);
        assertTrue(lastOut.contains("assigned-secret"), lastOut);

        Files.delete(dir.resolve("bad.json"));
        Files.writeString(dir.resolve("env.template"), "SMTP_PASSWORD=${SMTP_PASSWORD}\napi_key: LLM_API_REF\n"
                + "password: <set-in-n8n-credentials>\n", StandardCharsets.UTF_8);
        assertEquals(0, run("scan-secrets", dir.toString()), lastOut);
    }

    @Test
    void validateClientChecksTenantIsolation() throws IOException {
        initWithSample();
        assertEquals(0, run("parse-intake", SLUG), lastOut);
        assertEquals(0, run("validate-client", SLUG, "--require", "state,intake"), lastOut);
        assertEquals(1, run("validate-client", SLUG, "--require", "architecture-plan"));

        Path intakeFile = tmp.resolve("clients/" + SLUG + "/intake/intake.validated.json");
        ObjectNode intake = (ObjectNode) Workspace.readJson(intakeFile);
        intake.put("client_slug", "other-client");
        Workspace.writeJson(intakeFile, intake);
        assertEquals(1, run("validate-client", SLUG));
        assertTrue(lastOut.contains("tenant isolation"), lastOut);
    }

    @Test
    void qaReportCannotPassWithoutEvidence() throws IOException {
        Path report = tmp.resolve("qa-report.json");
        Files.writeString(report, """
                { "clientSlug": "apex-coaching", "generatedAt": "2026-10-29T18:00:00+05:30",
                  "status": "PASSED", "passed": true,
                  "traceability": [ { "requirement": "Enquiry form", "deliverable": "site.config.json", "status": "MET" } ],
                  "defects": [],
                  "securityChecks": { "secretScan": { "command": "orchbiz scan-secrets", "exitCode": 0 },
                    "consentRecorded": true, "tenantIsolationVerified": true, "unverifiedClaimsFlagged": true },
                  "evidence": [] }
                """, StandardCharsets.UTF_8);
        assertEquals(1, run("validate", "qa-report", report.toString()));
    }

    @Test
    void listSplittingRespectsParentheses() {
        assertEquals(List.of("Email", "WhatsApp (Approved, Template)", "Slack"),
                IntakeParser.splitList("Email, WhatsApp (Approved, Template), Slack"));
        assertEquals("45000", IntakeParser.parseAmount("INR 45,000").toPlainString());
        assertEquals("INR", IntakeParser.parseCurrency("₹2500 per month"));
        assertEquals("submission_date", IntakeParser.normaliseHeader("Timestamp"));
    }

    private static void copyTree(Path src, Path dst) throws IOException {
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : s.toList()) {
                Path target = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
