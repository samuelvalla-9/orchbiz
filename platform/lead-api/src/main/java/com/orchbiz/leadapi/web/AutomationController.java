package com.orchbiz.leadapi.web;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchbiz.leadapi.lead.ApiException;
import com.orchbiz.leadapi.lead.DraftRepository;
import com.orchbiz.leadapi.lead.LeadRepository;
import com.orchbiz.leadapi.lead.Tenant;

/**
 * Write-back endpoints for the classify-enquiry and draft-follow-up n8n workflows (admin token required).
 * Labels are validated against the fixed set from the classify-enquiry manifest.
 */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/leads/{leadId}")
public class AutomationController {

    static final Set<String> CLASSIFICATION_STATUSES = Set.of("classified", "needs-review", "pending");
    static final Set<String> INTENTS = Set.of("enrolment", "pricing", "demo-request", "schedule-question", "support", "other");
    static final Set<String> URGENCIES = Set.of("high", "normal", "low");
    static final Set<String> CHANNELS = Set.of("email", "whatsapp");
    static final Set<String> SOURCES = Set.of("template", "llm");
    private static final Pattern TENANT_ID = Pattern.compile(Tenant.ID_PATTERN);

    public record ClassificationRequest(String status, String intent, String urgency, String serviceInterest,
                                        Double confidence) {
    }

    public record DraftRequest(String channel, String subject, String body, String source) {
    }

    private final LeadRepository leads;
    private final DraftRepository drafts;
    private final ObjectMapper json;
    private final Clock clock;

    public AutomationController(LeadRepository leads, DraftRepository drafts, ObjectMapper json, Clock clock) {
        this.leads = leads;
        this.drafts = drafts;
        this.json = json;
        this.clock = clock;
    }

    @PutMapping("/classification")
    public Map<String, Object> classify(@PathVariable String tenantId, @PathVariable String leadId,
                                        @RequestBody ClassificationRequest req) {
        UUID id = requireLead(tenantId, leadId);
        Map<String, String> errors = new LinkedHashMap<>();
        if (req.status() == null || !CLASSIFICATION_STATUSES.contains(req.status())) {
            errors.put("status", "must be one of " + CLASSIFICATION_STATUSES);
            throw ApiException.validation(errors);
        }
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("status", req.status());
        if (!"pending".equals(req.status())) {
            if (req.intent() == null || !INTENTS.contains(req.intent())) {
                errors.put("intent", "must be one of " + INTENTS);
            }
            if (req.urgency() == null || !URGENCIES.contains(req.urgency())) {
                errors.put("urgency", "must be one of " + URGENCIES);
            }
            if (req.serviceInterest() == null || req.serviceInterest().isBlank() || req.serviceInterest().length() > 200) {
                errors.put("serviceInterest", "required, max 200 characters");
            }
            if (req.confidence() == null || req.confidence() < 0 || req.confidence() > 1) {
                errors.put("confidence", "must be between 0 and 1");
            }
            if (!errors.isEmpty()) {
                throw ApiException.validation(errors);
            }
            stored.put("intent", req.intent());
            stored.put("urgency", req.urgency());
            stored.put("serviceInterest", req.serviceInterest().trim());
            stored.put("confidence", req.confidence());
        }
        leads.updateClassification(tenantId, id, req.status(), write(stored), now());
        return stored;
    }

    @PostMapping("/drafts")
    public ResponseEntity<Map<String, Object>> createDraft(@PathVariable String tenantId, @PathVariable String leadId,
                                                           @RequestBody DraftRequest req) {
        UUID id = requireLead(tenantId, leadId);
        Map<String, String> errors = new LinkedHashMap<>();
        if (req.channel() == null || !CHANNELS.contains(req.channel())) {
            errors.put("channel", "must be one of " + CHANNELS);
        }
        if (req.body() == null || req.body().isBlank() || req.body().length() > 5000) {
            errors.put("body", "required, max 5000 characters");
        }
        if (req.subject() != null && req.subject().length() > 300) {
            errors.put("subject", "max 300 characters");
        }
        String source = req.source() == null ? "template" : req.source();
        if (!SOURCES.contains(source)) {
            errors.put("source", "must be one of " + SOURCES);
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        UUID draftId = UUID.randomUUID();
        drafts.insert(draftId, tenantId, id, req.channel(), req.subject(), req.body(), source, now());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("draftId", draftId.toString());
        body.put("channel", req.channel());
        body.put("subject", req.subject());
        body.put("body", req.body());
        body.put("status", DraftRepository.AWAITING_APPROVAL);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/drafts")
    public Map<String, Object> listDrafts(@PathVariable String tenantId, @PathVariable String leadId) {
        UUID id = requireLead(tenantId, leadId);
        List<Map<String, Object>> items = drafts.listForLead(tenantId, id).stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("draftId", d.id().toString());
            m.put("channel", d.channel());
            m.put("subject", d.subject());
            m.put("body", d.body());
            m.put("source", d.source());
            m.put("status", d.status());
            m.put("createdAt", d.createdAt().toString());
            return m;
        }).toList();
        return Map.of("leadId", id.toString(), "items", items);
    }

    private UUID requireLead(String tenantId, String leadId) {
        if (!TENANT_ID.matcher(tenantId).matches()) {
            throw ApiException.validation(Map.of("tenantId", "invalid"));
        }
        UUID id;
        try {
            id = UUID.fromString(leadId);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(Map.of("leadId", "invalid"));
        }
        // Tenant-scoped lookup: a lead from another tenant is reported as not found.
        if (!leads.exists(tenantId, id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "lead_not_found");
        }
        return id;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise classification", e);
        }
    }
}
