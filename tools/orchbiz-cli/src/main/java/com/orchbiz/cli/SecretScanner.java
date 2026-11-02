package com.orchbiz.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regex-based secret detection. Lines containing {@code orchbiz:allow-secret} are ignored. */
final class SecretScanner {

    record Finding(Path file, int line, String rule, String excerpt) { }

    private record Rule(String name, Pattern pattern, int valueGroup) { }

    static final String ALLOW_MARKER = "orchbiz:allow-secret";
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Set<String> SKIP_DIRS = Set.of(".git", "target", "node_modules", ".idea", ".angular", "dist");
    private static final Set<String> BINARY_EXT = Set.of("png", "jpg", "jpeg", "gif", "ico", "webp", "pdf", "zip", "jar",
            "class", "xlsx", "xls", "docx", "woff", "woff2", "ttf", "eot", "mp4", "mp3");

    private static final List<Rule> RULES = List.of(
            new Rule("private-key", Pattern.compile("-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----"), 0),
            new Rule("aws-access-key", Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"), 0),
            new Rule("github-token", Pattern.compile("\\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{40,})\\b"), 0),
            new Rule("openai-key", Pattern.compile("\\bsk-(?:proj-|ant-)?[A-Za-z0-9_-]{20,}\\b"), 0),
            new Rule("google-api-key", Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b"), 0),
            new Rule("slack-token", Pattern.compile("\\bxox[abprs]-[A-Za-z0-9-]{10,}\\b"), 0),
            new Rule("stripe-key", Pattern.compile("\\b(?:sk|rk)_live_[A-Za-z0-9]{16,}\\b"), 0),
            new Rule("jwt", Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"), 0),
            new Rule("url-credentials", Pattern.compile("\\b[a-z][a-z0-9+.-]*://[^\\s:/@\"']+:([^\\s:/@\"']{4,})@"), 1),
            new Rule("assigned-secret", Pattern.compile(
                    "(?i)[\"']?\\b(?:password|passwd|pwd|secret|client[_-]?secret|api[_-]?key|apikey|access[_-]?token|auth[_-]?token|bearer|private[_-]?key)\\b[\"']?\\s*[:=]\\s*[\"']?([^\\s\"',;<>{}()\\[\\]]{8,})"), 1)
    );

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)(\\$\\{|\\{\\{|<|_REF\\b|_REF$|change[_-]?me|placeholder|example|redacted|xxxx|\\*\\*\\*|your[_-]|replace|^\\$|process\\.env|getenv|env\\.|credentials\\.|\\$env:)");
    private static final Pattern ENV_NAME = Pattern.compile("^[A-Z][A-Z0-9_]+$");

    private SecretScanner() { }

    static boolean shouldScan(Path file) {
        for (Path part : file) {
            if (SKIP_DIRS.contains(part.toString())) return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && BINARY_EXT.contains(name.substring(dot + 1))) return false;
        try {
            return Files.size(file) <= MAX_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    static List<Finding> scan(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        for (int i = 0; i < Math.min(bytes.length, 8192); i++) {
            if (bytes[i] == 0) return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\\R", -1);
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            if (line.contains(ALLOW_MARKER)) continue;
            for (Rule rule : RULES) {
                Matcher m = rule.pattern.matcher(line);
                while (m.find()) {
                    String value = m.group(rule.valueGroup);
                    if (rule.valueGroup > 0 && isPlaceholder(value)) continue;
                    findings.add(new Finding(file, n + 1, rule.name, mask(m.group(0))));
                    break;
                }
            }
        }
        return findings;
    }

    static boolean isPlaceholder(String value) {
        return PLACEHOLDER.matcher(value).find() || ENV_NAME.matcher(value).matches();
    }

    private static String mask(String s) {
        String t = s.strip();
        if (t.length() <= 12) return t.charAt(0) + "***";
        return t.substring(0, 8) + "***" + t.substring(t.length() - 2);
    }
}
