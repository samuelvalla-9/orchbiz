package com.orchbiz.cli;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Classifies the modules requested in a validated intake against the purchased package definition. */
final class Scope {

    enum Status { INCLUDED, ADD_ON, OUT_OF_SCOPE }

    record Item(String category, String id, Status status) { }

    /** Package field -> manifest category. */
    static final Map<String, String> PACKAGE_MODULE_FIELDS = new LinkedHashMap<>();

    static {
        PACKAGE_MODULE_FIELDS.put("includedWebsiteModules", "website");
        PACKAGE_MODULE_FIELDS.put("optionalAddOnWebsiteModules", "website");
        PACKAGE_MODULE_FIELDS.put("includedAutomationModules", "automation");
        PACKAGE_MODULE_FIELDS.put("optionalAddOnAutomationModules", "automation");
    }

    final String packageId;
    final String packageVersion;
    final JsonNode pkg;
    final List<Item> items = new ArrayList<>();

    private Scope(String packageId, JsonNode pkg) {
        this.packageId = packageId;
        this.pkg = pkg;
        this.packageVersion = pkg.path("version").asText("?");
    }

    static Scope resolve(Workspace ws, JsonNode intake) {
        String packageId = intake.path("selected_package").asText("");
        if (packageId.isEmpty()) {
            throw new CliException("selected_package is missing from the validated intake.");
        }
        if (!Files.isRegularFile(ws.packageFile(packageId))) {
            throw new CliException("Package '" + packageId + "' has no definition at " + ws.relative(ws.packageFile(packageId))
                    + ". A 'custom' package needs an operator-written package file before planning.");
        }
        Scope scope = new Scope(packageId, Workspace.readJson(ws.packageFile(packageId)));
        scope.classify(intake.path("website_modules"), "website", "includedWebsiteModules", "optionalAddOnWebsiteModules");
        scope.classify(intake.path("automation_modules"), "automation", "includedAutomationModules", "optionalAddOnAutomationModules");
        return scope;
    }

    private void classify(JsonNode requested, String category, String includedField, String addOnField) {
        for (JsonNode r : requested) {
            String id = r.asText();
            Status s = contains(pkg.path(includedField), id) ? Status.INCLUDED
                    : contains(pkg.path(addOnField), id) ? Status.ADD_ON : Status.OUT_OF_SCOPE;
            items.add(new Item(category, id, s));
        }
    }

    private static boolean contains(JsonNode array, String id) {
        for (JsonNode n : array) {
            if (n.asText().equals(id)) return true;
        }
        return false;
    }
}
