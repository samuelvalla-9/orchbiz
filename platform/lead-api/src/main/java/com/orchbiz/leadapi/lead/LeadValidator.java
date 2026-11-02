package com.orchbiz.leadapi.lead;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** Server-side validation and normalisation of a lead submission against the tenant's form config. */
@Component
public class LeadValidator {

    static final int MAX_FIELDS = 40;
    static final int MAX_VALUE_LENGTH = 5000;
    static final int MAX_LIST_ITEMS = 50;
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s().-]");

    public record Normalised(
            Map<String, Object> fields,
            String emailNorm,
            String phoneNorm,
            OffsetDateTime clientSubmittedAt,
            String sourcePage) {
    }

    public Normalised validate(LeadSubmission s, Tenant tenant, String honeypotField) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> clean = new LinkedHashMap<>();

        if (s.fields() == null || s.fields().isEmpty()) {
            errors.put("fields", "required");
            throw ApiException.validation(errors);
        }
        if (s.fields().size() > MAX_FIELDS) {
            errors.put("fields", "too many fields (max " + MAX_FIELDS + ")");
            throw ApiException.validation(errors);
        }

        for (Map.Entry<String, Object> e : s.fields().entrySet()) {
            String key = e.getKey();
            if (key.equals(honeypotField)) {
                continue;
            }
            if (!KEY.matcher(key).matches()) {
                errors.put(key, "invalid field name");
                continue;
            }
            String problem = checkValue(e.getValue());
            if (problem != null) {
                errors.put(key, problem);
                continue;
            }
            clean.put(key, e.getValue() instanceof String str ? str.trim() : e.getValue());
        }

        for (String required : tenant.requiredFields()) {
            if (!errors.containsKey(required) && isBlank(clean.get(required))) {
                errors.put(required, "required");
            }
        }

        String emailNorm = null;
        Object email = clean.get(tenant.emailField());
        if (!isBlank(email) && !errors.containsKey(tenant.emailField())) {
            String v = email.toString().trim().toLowerCase(Locale.ROOT);
            if (v.length() > 320 || !EMAIL.matcher(v).matches()) {
                errors.put(tenant.emailField(), "invalid email address");
            } else {
                emailNorm = v;
            }
        }

        String phoneNorm = null;
        Object phone = clean.get(tenant.phoneField());
        if (!isBlank(phone) && !errors.containsKey(tenant.phoneField())) {
            String v = PHONE_SEPARATORS.matcher(phone.toString().trim()).replaceAll("");
            if (!PHONE.matcher(v).matches()) {
                errors.put(tenant.phoneField(), "invalid phone number");
            } else {
                phoneNorm = v;
            }
        }

        OffsetDateTime submittedAt = null;
        if (s.submittedAt() != null && !s.submittedAt().isBlank()) {
            try {
                submittedAt = OffsetDateTime.parse(s.submittedAt().trim());
            } catch (DateTimeParseException ex) {
                errors.put("submittedAt", "must be an ISO-8601 timestamp with offset");
            }
        }

        String sourcePage = s.sourcePage() == null ? null : s.sourcePage().trim();
        if (sourcePage != null && sourcePage.length() > 500) {
            errors.put("sourcePage", "too long (max 500)");
        }

        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return new Normalised(clean, emailNorm, phoneNorm, submittedAt, sourcePage);
    }

    public boolean honeypotTriggered(LeadSubmission s, String honeypotField) {
        if (s.fields() == null || honeypotField == null || honeypotField.isBlank()) {
            return false;
        }
        return !isBlank(s.fields().get(honeypotField));
    }

    private static String checkValue(Object v) {
        if (v == null || v instanceof Boolean || v instanceof Number) {
            return null;
        }
        if (v instanceof String s) {
            return s.length() > MAX_VALUE_LENGTH ? "too long (max " + MAX_VALUE_LENGTH + ")" : null;
        }
        if (v instanceof List<?> list) {
            if (list.size() > MAX_LIST_ITEMS) {
                return "too many items (max " + MAX_LIST_ITEMS + ")";
            }
            for (Object item : list) {
                if (item instanceof List<?> || item instanceof Map<?, ?>) {
                    return "unsupported value type";
                }
                String p = checkValue(item);
                if (p != null) {
                    return p;
                }
            }
            return null;
        }
        return "unsupported value type";
    }

    static boolean isBlank(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return s.isBlank();
        }
        if (v instanceof List<?> l) {
            return l.isEmpty();
        }
        return false;
    }
}
