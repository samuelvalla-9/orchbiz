package com.orchbiz.leadapi.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orchbiz.leadapi.lead.ApiException;
import com.orchbiz.leadapi.lead.LeadRepository;
import com.orchbiz.leadapi.lead.LeadService;
import com.orchbiz.leadapi.lead.Tenant;
import com.orchbiz.leadapi.lead.TenantRepository;

/** Operator-only endpoints (Bearer ORCHBIZ_ADMIN_TOKEN). Every query is scoped to the tenant in the path. */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}")
public class AdminController {

    private static final Pattern TENANT_ID = Pattern.compile(Tenant.ID_PATTERN);
    private static final Pattern FIELD_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    public record TenantRequest(
            String displayName,
            List<String> requiredFields,
            String emailField,
            String phoneField,
            Integer dedupeWindowHours,
            Boolean active) {
    }

    private final TenantRepository tenants;
    private final LeadRepository leads;
    private final LeadService leadService;

    public AdminController(TenantRepository tenants, LeadRepository leads, LeadService leadService) {
        this.tenants = tenants;
        this.leads = leads;
        this.leadService = leadService;
    }

    @PutMapping
    public Tenant upsertTenant(@PathVariable String tenantId, @RequestBody TenantRequest req) {
        checkTenantId(tenantId);
        Map<String, String> errors = new LinkedHashMap<>();
        if (req.displayName() == null || req.displayName().isBlank() || req.displayName().length() > 200) {
            errors.put("displayName", "required, max 200 characters");
        }
        List<String> required = req.requiredFields() == null ? List.of() : req.requiredFields();
        for (String f : required) {
            if (f == null || !FIELD_KEY.matcher(f).matches()) {
                errors.put("requiredFields", "invalid field name: " + f);
            }
        }
        String emailField = blankToDefault(req.emailField(), "email");
        String phoneField = blankToDefault(req.phoneField(), "phone");
        if (!FIELD_KEY.matcher(emailField).matches()) {
            errors.put("emailField", "invalid field name");
        }
        if (!FIELD_KEY.matcher(phoneField).matches()) {
            errors.put("phoneField", "invalid field name");
        }
        int window = req.dedupeWindowHours() == null ? 24 : req.dedupeWindowHours();
        if (window < 0 || window > 720) {
            errors.put("dedupeWindowHours", "must be between 0 and 720");
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return tenants.upsert(new Tenant(tenantId, req.displayName().trim(), required, emailField, phoneField, window,
                req.active() == null || req.active()));
    }

    @GetMapping
    public Tenant getTenant(@PathVariable String tenantId) {
        checkTenantId(tenantId);
        return tenants.find(tenantId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "tenant_not_found"));
    }

    @GetMapping("/leads")
    public Map<String, Object> listLeads(@PathVariable String tenantId,
                                         @RequestParam(defaultValue = "50") int limit) {
        checkTenantId(tenantId);
        int capped = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> items = leads.list(tenantId, capped).stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("leadId", row.id().toString());
            m.put("status", row.status());
            m.put("fields", leadService.readFields(row.fieldsJson()));
            m.put("sourcePage", row.sourcePage());
            m.put("firstReceivedAt", row.firstReceivedAt().toString());
            m.put("lastReceivedAt", row.lastReceivedAt().toString());
            m.put("submissionCount", row.submissionCount());
            return m;
        }).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("total", leads.count(tenantId));
        body.put("items", items);
        return body;
    }

    private static void checkTenantId(String tenantId) {
        if (!TENANT_ID.matcher(tenantId).matches()) {
            throw ApiException.validation(Map.of("tenantId", "invalid"));
        }
    }

    private static String blankToDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v.trim();
    }
}
