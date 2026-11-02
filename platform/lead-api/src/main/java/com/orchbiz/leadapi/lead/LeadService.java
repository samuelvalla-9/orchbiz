package com.orchbiz.leadapi.lead;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchbiz.leadapi.config.OrchbizProperties;
import com.orchbiz.leadapi.events.EventRepository;

@Service
public class LeadService {

    public static final String LEAD_CREATED = "lead.created";
    private static final Pattern TENANT_ID = Pattern.compile(Tenant.ID_PATTERN);

    public record Result(UUID leadId, String tenantId, boolean duplicate, boolean discarded) {
    }

    private final TenantRepository tenants;
    private final LeadRepository leads;
    private final EventRepository events;
    private final LeadValidator validator;
    private final OrchbizProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public LeadService(TenantRepository tenants, LeadRepository leads, EventRepository events, LeadValidator validator,
                       OrchbizProperties properties, ObjectMapper json, Clock clock) {
        this.tenants = tenants;
        this.leads = leads;
        this.events = events;
        this.validator = validator;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public Result submit(LeadSubmission s) {
        if (s.tenantId() == null || s.tenantId().isBlank()) {
            throw ApiException.validation(Map.of("tenantId", "required"));
        }
        if (!TENANT_ID.matcher(s.tenantId()).matches()) {
            throw ApiException.validation(Map.of("tenantId", "invalid"));
        }
        // Same response for unknown and inactive tenants so callers cannot probe which tenants exist.
        Tenant tenant = tenants.find(s.tenantId()).filter(Tenant::active)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "tenant_not_accepting_leads"));

        if (validator.honeypotTriggered(s, properties.honeypotField())) {
            return new Result(null, tenant.id(), false, true);
        }

        LeadValidator.Normalised n = validator.validate(s, tenant, properties.honeypotField());
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);

        var existing = leads.findRecentDuplicate(tenant.id(), n.emailNorm(), n.phoneNorm(),
                now.minusHours(tenant.dedupeWindowHours()));
        if (existing.isPresent()) {
            Map<String, Object> merged = new LinkedHashMap<>(readFields(existing.get().fieldsJson()));
            merged.putAll(n.fields());
            leads.recordRepeat(tenant.id(), existing.get().id(), n.emailNorm(), n.phoneNorm(), write(merged), now);
            return new Result(existing.get().id(), tenant.id(), true, false);
        }

        UUID leadId = UUID.randomUUID();
        leads.insert(leadId, tenant.id(), n.emailNorm(), n.phoneNorm(), write(n.fields()), n.sourcePage(),
                n.clientSubmittedAt(), now);

        UUID eventId = UUID.randomUUID();
        Map<String, Object> lead = new LinkedHashMap<>();
        lead.put("fields", n.fields());
        lead.put("sourcePage", n.sourcePage());
        lead.put("receivedAt", now.toString());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", LEAD_CREATED);
        payload.put("eventId", eventId.toString());
        payload.put("occurredAt", now.toString());
        payload.put("tenantId", tenant.id());
        payload.put("tenantName", tenant.displayName());
        payload.put("leadId", leadId.toString());
        payload.put("lead", lead);
        events.insertPending(eventId, tenant.id(), leadId, LEAD_CREATED, write(payload), now);

        return new Result(leadId, tenant.id(), false, false);
    }

    public Map<String, Object> readFields(String fieldsJson) {
        try {
            return json.readValue(fieldsJson, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored lead fields are not valid JSON", e);
        }
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise lead data", e);
        }
    }
}
