package com.orchbiz.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/** All orchbiz sub-commands. */
public final class Commands {

    private Commands() { }

    static String now() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    abstract static class Base implements Callable<Integer> {
        @Mixin
        OrchbizCli.RootOption rootOption;
        @Spec
        CommandSpec spec;

        PrintWriter out() { return spec.commandLine().getOut(); }

        Workspace ws() { return rootOption.workspace(); }

        int report(List<String> errors, List<String> warnings, String okMessage) {
            warnings.forEach(w -> out().println("WARN  " + w));
            errors.forEach(e -> out().println("FAIL  " + e));
            if (errors.isEmpty()) {
                out().println("PASS  " + okMessage);
                return OrchbizCli.OK;
            }
            out().println("RESULT: " + errors.size() + " problem(s) found.");
            return OrchbizCli.FAILED;
        }
    }

    // ------------------------------------------------------------------------------------------------ init-client
    @Command(name = "init-client", description = "Create clients/<slug>/ folders and state.json from the template.")
    static class InitClient extends Base {
        @Parameters(index = "0", description = "Client slug (lowercase kebab-case).")
        String slug;
        @Option(names = "--business-name", description = "Business name to record in state.json.")
        String businessName;
        @Option(names = "--package", defaultValue = "starter-lead-to-appointment", description = "Package id (default: ${DEFAULT-VALUE}).")
        String packageId;

        @Override
        public Integer call() throws IOException {
            Workspace ws = ws();
            Path dir = ws.clientDir(slug);
            Path state = ws.clientState(slug);
            if (Files.exists(state)) {
                throw new CliException(ws.relative(state) + " already exists; refusing to overwrite an existing engagement.");
            }
            if (!Files.isRegularFile(ws.packageFile(packageId))) {
                throw new CliException("Unknown package '" + packageId + "': " + ws.relative(ws.packageFile(packageId)) + " not found.");
            }
            for (String sub : List.of("intake", "spec", "deliverables/content", "deliverables/website",
                    "deliverables/automations", "qa/evidence", "handover")) {
                Files.createDirectories(dir.resolve(sub));
            }
            ObjectNode node = (ObjectNode) Workspace.readJson(ws.clientTemplateState());
            String ts = now();
            node.put("clientSlug", slug);
            node.put("businessName", businessName != null ? businessName : "TBD - set from intake");
            node.put("selectedPackage", packageId);
            node.put("status", "INTAKE_PENDING");
            node.put("createdAt", ts);
            node.put("updatedAt", ts);
            ArrayNode log = node.putArray("auditLog");
            ObjectNode entry = log.addObject();
            entry.put("timestamp", ts);
            entry.put("actor", "orchbiz-cli");
            entry.put("action", "CLIENT_INITIALIZED");
            entry.put("details", "Created client workspace from clients/_template/state.json");
            Workspace.writeJson(state, node);
            out().println("PASS  Created " + ws.relative(dir) + "/ (intake, spec, deliverables, qa/evidence, handover) and state.json");
            Boolean ignored = Git.isIgnored(ws.root(), state);
            if (Boolean.FALSE.equals(ignored)) {
                out().println("WARN  " + ws.relative(state) + " is NOT git-ignored. Check .gitignore before committing anything.");
            }
            out().println("NEXT  Put the questionnaire export (.csv or .xlsx) in " + ws.relative(dir.resolve("intake"))
                    + "/ and run: orchbiz parse-intake " + slug);
            return OrchbizCli.OK;
        }
    }

    // ------------------------------------------------------------------------------------------------ parse-intake
    @Command(name = "parse-intake", description = "Parse a .csv/.xlsx questionnaire into intake/intake.validated.json and validate it.")
    static class ParseIntake extends Base {
        @Parameters(index = "0", description = "Client slug.")
        String slug;
        @Parameters(index = "1", arity = "0..1", description = "Questionnaire file (default: the only .csv/.xlsx in clients/<slug>/intake/).")
        Path file;
        @Option(names = "--dry-run", description = "Print the normalised JSON instead of writing it.")
        boolean dryRun;

        @Override
        public Integer call() throws IOException {
            Workspace ws = ws();
            Path source = file != null ? file : findIntakeFile(ws);
            IntakeParser.Result result = new IntakeParser(Workspace.readJson(ws.intakeSchema())).parse(source, slug);
            ObjectNode intake = result.intake();
            out().println("INFO  Source: " + ws.relative(source));
            result.notes().forEach(n -> out().println("INFO  " + n));

            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            if (!intake.path("ai_data_consent").isBoolean() || !intake.path("ai_data_consent").asBoolean()) {
                errors.add("BLOCKER ai_data_consent is not true - stop; no AI processing may run on this intake.");
            }
            String fileSlug = intake.path("client_slug").asText("");
            if (!fileSlug.isEmpty() && !fileSlug.equals(slug)) {
                errors.add("client_slug in the questionnaire is '" + fileSlug + "' but the client folder is '" + slug + "'.");
            }
            errors.addAll(new SchemaValidator(ws).validate(ws.intakeSchema(), intake));

            if (dryRun) {
                out().println(Workspace.JSON.writeValueAsString(intake));
            } else if (errors.isEmpty()) {
                Path target = ws.validatedIntake(slug);
                Workspace.writeJson(target, intake);
                out().println("INFO  Wrote " + ws.relative(target));
            } else {
                warnings.add("intake.validated.json was NOT written because validation failed. "
                        + "Record these as blocking clarification questions.");
            }
            return report(errors, warnings, "Intake for '" + slug + "' is valid against intake/intake.schema.json");
        }

        private Path findIntakeFile(Workspace ws) throws IOException {
            Path dir = ws.clientDir(slug).resolve("intake");
            if (!Files.isDirectory(dir)) {
                throw new CliException(ws.relative(dir) + " does not exist. Run: orchbiz init-client " + slug);
            }
            List<Path> candidates;
            try (Stream<Path> s = Files.list(dir)) {
                candidates = s.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return (n.endsWith(".csv") || n.endsWith(".xlsx")) && !n.startsWith("~$");
                }).sorted().toList();
            }
            if (candidates.size() != 1) {
                throw new CliException("Expected exactly one .csv/.xlsx in " + ws.relative(dir) + " but found "
                        + candidates.size() + ". Pass the file path explicitly.");
            }
            return candidates.get(0);
        }
    }

    // ------------------------------------------------------------------------------------------------ validate
    @Command(name = "validate", description = "Validate JSON file(s) against a schema name or path. Names: "
            + "intake, manifest, package, state, architecture-plan, website-copy, site-config, workflows-config, qa-report.")
    static class Validate extends Base {
        @Parameters(index = "0", description = "Schema name or workspace-relative schema path.")
        String schema;
        @Parameters(index = "1..*", arity = "1..*", description = "JSON file(s) to validate.")
        List<Path> files;

        @Override
        public Integer call() {
            Workspace ws = ws();
            SchemaValidator v = new SchemaValidator(ws);
            List<String> errors = new ArrayList<>();
            for (Path f : files) {
                List<String> e = v.validate(schema, Workspace.readJson(f));
                if (e.isEmpty()) {
                    out().println("OK    " + ws.relative(f));
                }
                e.forEach(msg -> errors.add(ws.relative(f) + ": " + msg));
            }
            return report(errors, List.of(), files.size() + " file(s) valid against '" + schema + "'");
        }
    }

    // ------------------------------------------------------------------------------------------------ validate-client
    @Command(name = "validate-client", description = "Validate every known artifact present in clients/<slug>/ against its schema.")
    static class ValidateClient extends Base {
        @Parameters(index = "0", description = "Client slug.")
        String slug;
        @Option(names = "--require", split = ",", description = "Artifacts that must exist (state,intake,architecture-plan,website-copy,site-config,workflows-config,qa-report).")
        List<String> required = new ArrayList<>();

        static final Map<String, String> ARTIFACTS = new LinkedHashMap<>();

        static {
            ARTIFACTS.put("state", "state.json");
            ARTIFACTS.put("intake", "intake/intake.validated.json");
            ARTIFACTS.put("architecture-plan", "spec/architecture-plan.json");
            ARTIFACTS.put("website-copy", "deliverables/content/website-copy.json");
            ARTIFACTS.put("site-config", "deliverables/website/site.config.json");
            ARTIFACTS.put("workflows-config", "deliverables/automations/workflows-config.json");
            ARTIFACTS.put("qa-report", "qa/qa-report.json");
        }

        @Override
        public Integer call() {
            Workspace ws = ws();
            Path dir = ws.clientDir(slug);
            if (!Files.isDirectory(dir)) {
                throw new CliException(ws.relative(dir) + " does not exist.");
            }
            SchemaValidator v = new SchemaValidator(ws);
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            for (String r : required) {
                if (!ARTIFACTS.containsKey(r)) {
                    throw new CliException("Unknown artifact '" + r + "'. Known: " + ARTIFACTS.keySet());
                }
            }
            for (Map.Entry<String, String> a : ARTIFACTS.entrySet()) {
                Path f = dir.resolve(a.getValue());
                if (!Files.isRegularFile(f)) {
                    if (required.contains(a.getKey())) {
                        errors.add("Missing required artifact " + ws.relative(f));
                    } else {
                        out().println("SKIP  " + ws.relative(f) + " (not present)");
                    }
                    continue;
                }
                JsonNode node = Workspace.readJson(f);
                List<String> e = v.validate(a.getKey(), node);
                if (e.isEmpty()) {
                    out().println("OK    " + ws.relative(f));
                }
                e.forEach(msg -> errors.add(ws.relative(f) + ": " + msg));
                String artifactSlug = node.has("clientSlug") ? node.path("clientSlug").asText()
                        : node.path("client_slug").asText(null);
                if (artifactSlug != null && !artifactSlug.equals(slug)) {
                    errors.add(ws.relative(f) + ": belongs to client '" + artifactSlug + "', not '" + slug + "' (tenant isolation).");
                }
            }
            return report(errors, warnings, "All present artifacts for '" + slug + "' are schema-valid");
        }
    }

    // ------------------------------------------------------------------------------------------------ check-scope
    @Command(name = "check-scope", description = "Compare requested modules in the validated intake with the purchased package.")
    static class CheckScope extends Base {
        @Parameters(index = "0", description = "Client slug.")
        String slug;

        @Override
        public Integer call() {
            Workspace ws = ws();
            JsonNode intake = Workspace.readJson(ws.validatedIntake(slug));
            Scope scope = Scope.resolve(ws, intake);
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            out().println("INFO  Package: " + scope.packageId + " v" + scope.packageVersion);
            for (Scope.Item item : scope.items) {
                switch (item.status()) {
                    case INCLUDED -> out().println("IN    " + item.category() + "/" + item.id());
                    case ADD_ON -> warnings.add("ADD-ON " + item.category() + "/" + item.id()
                            + " is an optional add-on: confirm it is priced and agreed before Gate 1.");
                    case OUT_OF_SCOPE -> errors.add("OUT-OF-SCOPE " + item.category() + "/" + item.id()
                            + " is not part of package '" + scope.packageId + "'. List it under Out-of-Scope / Phase-2 in requirements-brief.md.");
                }
                if (item.status() != Scope.Status.OUT_OF_SCOPE && !Files.isRegularFile(ws.manifest(item.category(), item.id()))) {
                    errors.add("No catalogue manifest for " + item.category() + "/" + item.id() + " ("
                            + ws.relative(ws.manifest(item.category(), item.id())) + ").");
                }
            }
            return report(errors, warnings, "All requested modules are within package '" + scope.packageId + "'");
        }
    }

    // ------------------------------------------------------------------------------------------------ check-budget
    @Command(name = "check-budget", description = "Estimate monthly operating cost from catalogue manifests and compare with the client's budget.")
    static class CheckBudget extends Base {
        @Parameters(index = "0", description = "Client slug.")
        String slug;
        @Option(names = "--json", description = "Print a costEstimate object suitable for spec/architecture-plan.json.")
        boolean json;

        @Override
        public Integer call() throws IOException {
            Workspace ws = ws();
            JsonNode intake = Workspace.readJson(ws.validatedIntake(slug));
            Scope scope = Scope.resolve(ws, intake);
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            ObjectNode est = Workspace.JSON.createObjectNode();
            est.put("currency", "USD");
            BigDecimal base = BigDecimal.valueOf(scope.pkg.path("baseInfrastructure").path("estimatedMonthlyCostUSD").asDouble(0));
            est.put("baseMonthlyCostUSD", base);
            BigDecimal total = base;
            ArrayNode modules = est.putArray("modules");
            for (Scope.Item item : scope.items) {
                if (item.status() == Scope.Status.OUT_OF_SCOPE) continue;
                Path mf = ws.manifest(item.category(), item.id());
                if (!Files.isRegularFile(mf)) {
                    errors.add("No manifest for " + item.id() + "; cost cannot be estimated.");
                    continue;
                }
                JsonNode manifest = Workspace.readJson(mf);
                BigDecimal cost = BigDecimal.valueOf(manifest.path("estimatedMonthlyCostUSD").asDouble(0));
                ObjectNode m = modules.addObject();
                m.put("id", item.id());
                m.put("version", manifest.path("version").asText());
                m.put("monthlyCostUSD", cost);
                total = total.add(cost);
            }
            est.put("totalMonthlyCostUSD", total);

            String currency = intake.path("monthly_ops_budget_currency").asText("");
            JsonNode amountNode = intake.path("monthly_ops_budget_amount");
            ObjectNode budget = est.putObject("clientBudget");
            if (currency.isEmpty() || !amountNode.isNumber()) {
                errors.add("monthly_ops_budget_amount / monthly_ops_budget_currency missing in the validated intake.");
            } else {
                BigDecimal amount = amountNode.decimalValue();
                budget.put("amount", amount);
                budget.put("currency", currency);
                JsonNode fx = Workspace.readJson(ws.fxRates());
                JsonNode rate = fx.path("ratesPerUSD").path(currency);
                if (!rate.isNumber()) {
                    errors.add("No FX rate for " + currency + " in " + ws.relative(ws.fxRates()) + ".");
                } else {
                    BigDecimal usd = amount.divide(rate.decimalValue(), 2, RoundingMode.HALF_UP);
                    budget.put("amountUSD", usd);
                    budget.put("fxRatePerUSD", rate.decimalValue());
                    budget.put("fxAsOf", fx.path("asOf").asText());
                    boolean within = total.compareTo(usd) <= 0;
                    est.put("withinBudget", within);
                    if (!within) {
                        errors.add("Estimated USD " + total + "/month exceeds client budget " + currency + " " + amount
                                + " (~USD " + usd + "/month). Halt and flag at Gate 1.");
                    }
                }
            }
            if (scope.pkg.path("baseInfrastructure").path("operatorReviewRequired").asBoolean(false)) {
                warnings.add("Package base infrastructure cost is an operator estimate - confirm before quoting.");
            }
            if (json) {
                out().println(Workspace.JSON.writeValueAsString(est));
            } else {
                out().println("INFO  Base infrastructure: USD " + base);
                modules.forEach(m -> out().println("INFO  " + m.path("id").asText() + " v" + m.path("version").asText()
                        + ": USD " + m.path("monthlyCostUSD").asText()));
                out().println("INFO  Total estimate: USD " + total + "/month");
                if (budget.has("amountUSD")) {
                    out().println("INFO  Client budget: " + budget.path("currency").asText() + " " + budget.path("amount").asText()
                            + " (~USD " + budget.path("amountUSD").asText() + " at " + budget.path("fxRatePerUSD").asText()
                            + "/USD, " + budget.path("fxAsOf").asText() + ")");
                }
            }
            return report(errors, warnings, "Estimated monthly cost is within the client's budget");
        }
    }

    // ------------------------------------------------------------------------------------------------ check-gate
    @Command(name = "check-gate", description = "Fail unless the given approval gate is recorded as approved in state.json.")
    static class CheckGate extends Base {
        @Parameters(index = "0", description = "Client slug.")
        String slug;
        @Parameters(index = "1", description = "gate1 or gate2.")
        String gate;

        static final Map<String, String> GATES = Map.of(
                "gate1", "gate1_scope_and_architecture",
                "gate2", "gate2_release_and_deploy");

        @Override
        public Integer call() {
            Workspace ws = ws();
            List<String> toCheck = switch (gate) {
                case "gate1" -> List.of("gate1");
                case "gate2" -> List.of("gate1", "gate2");
                default -> throw new CliException("Unknown gate '" + gate + "' (use gate1 or gate2).");
            };
            JsonNode state = Workspace.readJson(ws.clientState(slug));
            List<String> errors = new ArrayList<>();
            for (String g : toCheck) {
                String key = GATES.get(g);
                JsonNode a = state.path("approvals").path(key);
                if (!a.path("approved").isBoolean() || !a.path("approved").asBoolean()) {
                    errors.add(key + ".approved is not true in " + ws.relative(ws.clientState(slug))
                            + ". Only the operator may set it after reviewing the gate artifacts.");
                    continue;
                }
                if (a.path("approvedBy").asText("").isBlank()) {
                    errors.add(key + ".approvedBy must name the human approver.");
                }
                if (a.path("approvedAt").asText("").isBlank()) {
                    errors.add(key + ".approvedAt must record when approval was given.");
                }
                if (errors.isEmpty()) {
                    out().println("OK    " + key + " approved by " + a.path("approvedBy").asText() + " at " + a.path("approvedAt").asText());
                }
            }
            return report(errors, List.of(), GATES.get(gate) + " is approved for '" + slug + "'");
        }
    }

    // ------------------------------------------------------------------------------------------------ scan-secrets
    @Command(name = "scan-secrets", description = "Scan files for hard-coded secrets (keys, tokens, passwords).")
    static class ScanSecrets extends Base {
        @Parameters(arity = "0..*", description = "Files or directories to scan.")
        List<Path> paths = new ArrayList<>();
        @Option(names = "--staged", description = "Scan files staged in git (used by the pre-commit hook).")
        boolean staged;

        @Override
        public Integer call() throws IOException {
            Workspace ws = ws();
            List<Path> targets = new ArrayList<>(paths);
            if (staged) {
                List<String> names = Git.stagedFiles(ws.root());
                if (names == null) {
                    throw new CliException("git is not available; cannot list staged files.");
                }
                names.forEach(n -> targets.add(ws.root().resolve(n)));
                if (targets.isEmpty()) {
                    out().println("PASS  No staged files to scan");
                    return OrchbizCli.OK;
                }
            }
            if (targets.isEmpty()) {
                throw new CliException("Nothing to scan: pass paths or --staged.");
            }
            List<SecretScanner.Finding> findings = new ArrayList<>();
            int scanned = 0;
            for (Path t : targets) {
                if (!Files.exists(t)) continue;
                List<Path> files;
                try (Stream<Path> s = Files.isDirectory(t) ? Files.walk(t) : Stream.of(t)) {
                    files = s.filter(Files::isRegularFile).filter(SecretScanner::shouldScan).toList();
                }
                for (Path f : files) {
                    scanned++;
                    findings.addAll(SecretScanner.scan(f));
                }
            }
            List<String> errors = new ArrayList<>();
            for (SecretScanner.Finding f : findings) {
                errors.add(ws.relative(f.file()) + ":" + f.line() + " [" + f.rule() + "] " + f.excerpt());
            }
            if (!errors.isEmpty()) {
                out().println("HINT  Replace values with credential references (e.g. SMTP_CREDENTIAL_REF) or ${ENV_VAR} placeholders.");
            }
            return report(errors, List.of(), scanned + " file(s) scanned, no secrets found");
        }
    }

    // ------------------------------------------------------------------------------------------------ validate-catalogue
    @Command(name = "validate-catalogue", description = "Validate all module manifests and package definitions in catalogue/.")
    static class ValidateCatalogue extends Base {
        @Override
        public Integer call() throws IOException {
            Workspace ws = ws();
            SchemaValidator v = new SchemaValidator(ws);
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            Map<String, JsonNode> manifests = new LinkedHashMap<>();

            for (String[] folder : new String[][]{{"websites", "website"}, {"automations", "automation"}}) {
                Path dir = ws.catalogue().resolve(folder[0]);
                if (!Files.isDirectory(dir)) continue;
                List<Path> moduleDirs;
                try (Stream<Path> s = Files.list(dir)) {
                    moduleDirs = s.filter(Files::isDirectory).sorted().toList();
                }
                for (Path md : moduleDirs) {
                    Path mf = md.resolve("manifest.json");
                    String rel = ws.relative(mf);
                    if (!Files.isRegularFile(mf)) {
                        errors.add(rel + " is missing.");
                        continue;
                    }
                    JsonNode m = Workspace.readJson(mf);
                    List<String> e = v.validate(ws.templateSchema(), m);
                    e.forEach(msg -> errors.add(rel + ": " + msg));
                    String id = m.path("id").asText();
                    if (!id.equals(md.getFileName().toString())) {
                        errors.add(rel + ": id '" + id + "' does not match folder name '" + md.getFileName() + "'.");
                    }
                    if (!folder[1].equals(m.path("category").asText())) {
                        errors.add(rel + ": category must be '" + folder[1] + "' for modules under catalogue/" + folder[0] + "/.");
                    }
                    if (manifests.put(id, m) != null) {
                        errors.add(rel + ": duplicate module id '" + id + "'.");
                    }
                    checkImplementations(ws, rel, m, errors);
                    if (e.isEmpty()) {
                        out().println("OK    " + rel + " (" + id + " v" + m.path("version").asText() + ", "
                                + m.path("implementationStatus").asText("unspecified") + ")");
                    }
                }
            }
            for (Map.Entry<String, JsonNode> m : manifests.entrySet()) {
                for (JsonNode dep : m.getValue().path("dependencies")) {
                    if (!manifests.containsKey(dep.asText())) {
                        errors.add("Module '" + m.getKey() + "' depends on unknown module '" + dep.asText() + "'.");
                    }
                }
            }

            JsonNode intakeSchema = Workspace.readJson(ws.intakeSchema());
            Set<String> intakeEnums = new LinkedHashSet<>();
            intakeSchema.path("properties").path("website_modules").path("items").path("enum").forEach(n -> intakeEnums.add(n.asText()));
            intakeSchema.path("properties").path("automation_modules").path("items").path("enum").forEach(n -> intakeEnums.add(n.asText()));
            for (String id : manifests.keySet()) {
                if (!intakeEnums.contains(id)) {
                    warnings.add("Module '" + id + "' is not selectable in intake/intake.schema.json.");
                }
            }
            for (String id : intakeEnums) {
                if (!manifests.containsKey(id)) {
                    errors.add("Intake allows module '" + id + "' but the catalogue has no manifest for it.");
                }
            }

            if (Files.isDirectory(ws.packagesDir())) {
                List<Path> pkgs;
                try (Stream<Path> s = Files.list(ws.packagesDir())) {
                    pkgs = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
                }
                for (Path p : pkgs) {
                    String rel = ws.relative(p);
                    JsonNode pkg = Workspace.readJson(p);
                    List<String> e = v.validate("package", pkg);
                    e.forEach(msg -> errors.add(rel + ": " + msg));
                    String expectedId = p.getFileName().toString().replaceFirst("\\.json$", "");
                    if (!expectedId.equals(pkg.path("packageId").asText())) {
                        errors.add(rel + ": packageId must equal the file name '" + expectedId + "'.");
                    }
                    for (String field : Scope.PACKAGE_MODULE_FIELDS.keySet()) {
                        for (JsonNode id : pkg.path(field)) {
                            JsonNode m = manifests.get(id.asText());
                            if (m == null) {
                                errors.add(rel + ": " + field + " references '" + id.asText() + "' which has no manifest.");
                            } else if (!Scope.PACKAGE_MODULE_FIELDS.get(field).equals(m.path("category").asText())) {
                                errors.add(rel + ": " + field + " lists '" + id.asText() + "' but its category is "
                                        + m.path("category").asText() + ".");
                            }
                        }
                    }
                    if (e.isEmpty()) {
                        out().println("OK    " + rel);
                    }
                }
            }
            return report(errors, warnings, manifests.size() + " module manifest(s) and all packages are valid");
        }

        static final java.util.regex.Pattern CREDENTIAL_REF = java.util.regex.Pattern.compile("^[A-Z][A-Z0-9_]*_REF$");

        /** Implementation pointers must exist; n8n blueprints must be importable, inactive and credential-by-reference. */
        static void checkImplementations(Workspace ws, String rel, JsonNode manifest, List<String> errors) throws IOException {
            String status = manifest.path("implementationStatus").asText("contract-only");
            if (!"contract-only".equals(status) && manifest.path("implementation").isEmpty()) {
                errors.add(rel + ": implementationStatus '" + status + "' requires at least one implementation entry.");
            }
            for (JsonNode impl : manifest.path("implementation")) {
                String path = impl.path("path").asText();
                Path target = ws.root().resolve(path).normalize();
                if (!target.startsWith(ws.root()) || path.contains("..")) {
                    errors.add(rel + ": implementation path '" + path + "' must stay inside the workspace.");
                    continue;
                }
                if (!Files.exists(target)) {
                    errors.add(rel + ": implementation path '" + path + "' does not exist.");
                    continue;
                }
                if ("n8n-workflow".equals(impl.path("kind").asText())) {
                    if (!Files.isRegularFile(target) || !path.endsWith(".workflow.json")) {
                        errors.add(rel + ": n8n-workflow path '" + path + "' must be a *.workflow.json file.");
                        continue;
                    }
                    checkN8nBlueprint(path, Workspace.readJson(target), errors);
                }
            }
        }

        static void checkN8nBlueprint(String rel, JsonNode wf, List<String> errors) {
            JsonNode nodes = wf.path("nodes");
            if (!nodes.isArray() || nodes.isEmpty()) {
                errors.add(rel + ": blueprint must contain a non-empty 'nodes' array.");
                return;
            }
            if (!wf.path("connections").isObject()) {
                errors.add(rel + ": blueprint must contain a 'connections' object.");
            }
            if (wf.path("active").asBoolean(false)) {
                errors.add(rel + ": blueprint must be exported with active=false; activation is an operator action.");
            }
            Set<String> names = new LinkedHashSet<>();
            for (JsonNode n : nodes) {
                String name = n.path("name").asText();
                if (name.isBlank() || n.path("type").asText().isBlank()) {
                    errors.add(rel + ": every node needs a name and a type.");
                }
                if (!names.add(name)) {
                    errors.add(rel + ": duplicate node name '" + name + "'.");
                }
                var creds = n.path("credentials").fields();
                while (creds.hasNext()) {
                    var c = creds.next();
                    String credName = c.getValue().path("name").asText();
                    if (!CREDENTIAL_REF.matcher(credName).matches()) {
                        errors.add(rel + ": node '" + name + "' credential '" + c.getKey()
                                + "' must be referenced by a *_REF name, not '" + credName + "'.");
                    }
                    if (!c.getValue().path("id").asText("").isEmpty()) {
                        errors.add(rel + ": node '" + name + "' credential '" + c.getKey()
                                + "' must not carry an instance-specific id.");
                    }
                }
            }
            var conns = wf.path("connections").fields();
            while (conns.hasNext()) {
                var c = conns.next();
                if (!names.contains(c.getKey())) {
                    errors.add(rel + ": connection from unknown node '" + c.getKey() + "'.");
                }
                for (JsonNode output : c.getValue().path("main")) {
                    for (JsonNode link : output) {
                        if (!names.contains(link.path("node").asText())) {
                            errors.add(rel + ": connection to unknown node '" + link.path("node").asText() + "'.");
                        }
                    }
                }
            }
        }
    }
}
