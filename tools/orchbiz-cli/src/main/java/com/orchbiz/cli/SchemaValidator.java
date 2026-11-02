package com.orchbiz.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** JSON Schema (draft 2020-12) validation against schema files in the workspace. */
public final class SchemaValidator {

    /** Short names accepted by {@code orchbiz validate} mapped to workspace-relative schema paths. */
    public static final Map<String, String> NAMED_SCHEMAS;

    static {
        Map<String, String> m = new TreeMap<>();
        m.put("intake", "intake/intake.schema.json");
        m.put("manifest", "catalogue/template.schema.json");
        m.put("package", "schemas/package.schema.json");
        m.put("state", "schemas/state.schema.json");
        m.put("architecture-plan", "schemas/architecture-plan.schema.json");
        m.put("website-copy", "schemas/website-copy.schema.json");
        m.put("site-config", "schemas/site-config.schema.json");
        m.put("workflows-config", "schemas/workflows-config.schema.json");
        m.put("qa-report", "schemas/qa-report.schema.json");
        NAMED_SCHEMAS = m;
    }

    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private final Workspace ws;

    public SchemaValidator(Workspace ws) {
        this.ws = ws;
    }

    public Path resolveSchema(String nameOrPath) {
        String rel = NAMED_SCHEMAS.get(nameOrPath);
        Path p = rel != null ? ws.root().resolve(rel) : ws.root().resolve(nameOrPath);
        if (!p.toFile().isFile()) {
            throw new CliException("Schema not found: " + nameOrPath + " (known names: " + NAMED_SCHEMAS.keySet() + ")");
        }
        return p;
    }

    public List<String> validate(String schemaNameOrPath, JsonNode instance) {
        return validate(resolveSchema(schemaNameOrPath), instance);
    }

    public List<String> validate(Path schemaFile, JsonNode instance) {
        JsonSchema schema = FACTORY.getSchema(Workspace.readJson(schemaFile));
        Set<ValidationMessage> errors = schema.validate(instance);
        return errors.stream().map(ValidationMessage::getMessage).sorted().collect(Collectors.toList());
    }
}
