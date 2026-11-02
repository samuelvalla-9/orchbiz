package com.orchbiz.leadapi.lead;

import java.util.List;

public record Tenant(
        String id,
        String displayName,
        List<String> requiredFields,
        String emailField,
        String phoneField,
        int dedupeWindowHours,
        boolean active) {

    public static final String ID_PATTERN = "^[a-z0-9][a-z0-9-]{1,63}$";
}
