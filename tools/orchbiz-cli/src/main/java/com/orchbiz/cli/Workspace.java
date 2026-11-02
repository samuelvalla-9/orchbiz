package com.orchbiz.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Resolves well-known paths inside an OrchBiz workspace and provides shared JSON I/O. */
public final class Workspace {

    public static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    public static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path root;

    public Workspace(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Walks up from {@code start} until a directory containing {@code catalogue/template.schema.json} is found. */
    public static Workspace locate(Path start) {
        Path dir = start.toAbsolutePath().normalize();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("catalogue").resolve("template.schema.json"))) {
                return new Workspace(dir);
            }
            dir = dir.getParent();
        }
        throw new CliException("Could not locate the OrchBiz workspace root (no catalogue/template.schema.json "
                + "found above " + start.toAbsolutePath() + "). Use --root.");
    }

    public Path root() { return root; }
    public Path intakeSchema() { return root.resolve("intake").resolve("intake.schema.json"); }
    public Path templateSchema() { return root.resolve("catalogue").resolve("template.schema.json"); }
    public Path schemasDir() { return root.resolve("schemas"); }
    public Path catalogue() { return root.resolve("catalogue"); }
    public Path packagesDir() { return catalogue().resolve("packages"); }
    public Path fxRates() { return catalogue().resolve("fx-rates.json"); }
    public Path clientsDir() { return root.resolve("clients"); }
    public Path clientTemplateState() { return clientsDir().resolve("_template").resolve("state.json"); }

    public Path packageFile(String packageId) { return packagesDir().resolve(packageId + ".json"); }

    public Path manifest(String category, String moduleId) {
        String folder = "website".equals(category) ? "websites" : "automations";
        return catalogue().resolve(folder).resolve(moduleId).resolve("manifest.json");
    }

    public Path clientDir(String slug) {
        requireSlug(slug);
        return clientsDir().resolve(slug);
    }

    public Path clientState(String slug) { return clientDir(slug).resolve("state.json"); }
    public Path validatedIntake(String slug) { return clientDir(slug).resolve("intake").resolve("intake.validated.json"); }

    public static void requireSlug(String slug) {
        if (slug == null || !SLUG.matcher(slug).matches() || slug.startsWith("_")) {
            throw new CliException("Invalid client slug '" + slug + "': use lowercase kebab-case, e.g. apex-coaching");
        }
    }

    public static JsonNode readJson(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new CliException("File not found: " + file);
        }
        try {
            return JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CliException("Invalid JSON in " + file + ": " + e.getMessage());
        }
    }

    public static void writeJson(Path file, JsonNode node) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.writeValueAsString(node) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CliException("Could not write " + file + ": " + e.getMessage());
        }
    }

    public String relative(Path p) {
        Path abs = p.toAbsolutePath().normalize();
        return abs.startsWith(root) ? root.relativize(abs).toString().replace('\\', '/') : abs.toString();
    }
}
