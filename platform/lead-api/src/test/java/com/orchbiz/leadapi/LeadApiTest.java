package com.orchbiz.leadapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

/** Covers catalogue test cases CSL-01..04 and ENQ-01/ENQ-03 plus admin and isolation rules. */
class LeadApiTest extends AbstractApiTest {

    @Test
    void csl01_validLeadIsStoredWithTenantAndEventQueued() throws Exception {
        String body = postLead(validLead(TENANT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.storedIn[0]").value("postgresql"))
                .andReturn().getResponse().getContentAsString();
        String leadId = json.readTree(body).get("leadId").asText();

        assertThat(count("leads")).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM leads");
        assertThat(row.get("tenant_id")).isEqualTo(TENANT);
        assertThat(row.get("email_norm")).isEqualTo("parent@example.com");
        assertThat(row.get("phone_norm")).isEqualTo("+919876543210");

        Map<String, Object> event = jdbc.queryForMap("SELECT * FROM lead_events");
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("event_type")).isEqualTo("lead.created");
        JsonNode payload = json.readTree((String) event.get("payload_json"));
        assertThat(payload.get("leadId").asText()).isEqualTo(leadId);
        assertThat(payload.get("tenantId").asText()).isEqualTo(TENANT);
        assertThat(payload.at("/lead/fields/course_interest").asText()).isEqualTo("JEE Foundation");
    }

    @Test
    void csl02_sameContactWithinWindowIsDuplicateAndNotNotifiedAgain() throws Exception {
        String first = postLead(validLead(TENANT)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        clock.advance(Duration.ofHours(3));

        Map<String, Object> again = new HashMap<>(validLead(TENANT));
        Map<String, Object> fields = new HashMap<>(castMap(again.get("fields")));
        fields.put("email", "  PARENT@example.com ");
        fields.put("message", "Second message");
        again.put("fields", fields);

        postLead(again)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.leadId").value(json.readTree(first).get("leadId").asText()));

        assertThat(count("leads")).isEqualTo(1);
        assertThat(count("lead_events")).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT submission_count, fields_json FROM leads");
        assertThat(((Number) row.get("submission_count")).intValue()).isEqualTo(2);
        assertThat((String) row.get("fields_json")).contains("Second message");
    }

    @Test
    void sameContactAfterWindowCreatesNewLead() throws Exception {
        postLead(validLead(TENANT)).andExpect(status().isCreated());
        clock.advance(Duration.ofHours(25));
        postLead(validLead(TENANT)).andExpect(status().isCreated()).andExpect(jsonPath("$.duplicate").value(false));
        assertThat(count("leads")).isEqualTo(2);
        assertThat(count("lead_events")).isEqualTo(2);
    }

    @Test
    void csl03_missingRequiredFieldIsRejectedAndNothingStored() throws Exception {
        Map<String, Object> lead = new HashMap<>(validLead(TENANT));
        Map<String, Object> fields = new HashMap<>(castMap(lead.get("fields")));
        fields.remove("course_interest");
        fields.put("full_name", "   ");
        lead.put("fields", fields);

        postLead(lead)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors.course_interest").value("required"))
                .andExpect(jsonPath("$.fieldErrors.full_name").value("required"));
        assertThat(count("leads")).isZero();
        assertThat(count("lead_events")).isZero();
    }

    @Test
    void enq03_invalidEmailPhoneAndTimestampAreRejected() throws Exception {
        Map<String, Object> lead = new HashMap<>(validLead(TENANT));
        Map<String, Object> fields = new HashMap<>(castMap(lead.get("fields")));
        fields.put("email", "not-an-email");
        fields.put("phone", "12ab");
        lead.put("fields", fields);
        lead.put("submittedAt", "yesterday");

        postLead(lead)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("invalid email address"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("invalid phone number"))
                .andExpect(jsonPath("$.fieldErrors.submittedAt").exists());
        assertThat(count("leads")).isZero();
    }

    @Test
    void nestedObjectsAndBadFieldNamesAreRejected() throws Exception {
        Map<String, Object> lead = new HashMap<>(validLead(TENANT));
        Map<String, Object> fields = new HashMap<>(castMap(lead.get("fields")));
        fields.put("extra", Map.of("x", 1));
        fields.put("bad key!", "v");
        lead.put("fields", fields);

        postLead(lead)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.extra").value("unsupported value type"))
                .andExpect(jsonPath("$.fieldErrors['bad key!']").value("invalid field name"));
    }

    @Test
    void malformedJsonAndMissingTenantAreBadRequests() throws Exception {
        postLead("{not json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("malformed_json"));
        postLead(Map.of("fields", Map.of("full_name", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.tenantId").value("required"));
        postLead(Map.of("tenantId", "Bad Tenant!", "fields", Map.of("a", "b")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.tenantId").value("invalid"));
    }

    @Test
    void csl04_unknownOrInactiveTenantIsForbiddenWithSameResponse() throws Exception {
        postLead(validLead("someone-else"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("tenant_not_accepting_leads"));

        createTenant("paused-client", false);
        postLead(validLead("paused-client"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("tenant_not_accepting_leads"));
        assertThat(count("leads")).isZero();
    }

    @Test
    void honeypotSubmissionIsAcceptedButDiscarded() throws Exception {
        Map<String, Object> lead = new HashMap<>(validLead(TENANT));
        Map<String, Object> fields = new HashMap<>(castMap(lead.get("fields")));
        fields.put("_hp", "http://spam.example");
        lead.put("fields", fields);

        postLead(lead).andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(true));
        assertThat(count("leads")).isZero();
        assertThat(count("lead_events")).isZero();
    }

    @Test
    void honeypotFieldIsNotStoredWhenEmpty() throws Exception {
        Map<String, Object> lead = new HashMap<>(validLead(TENANT));
        Map<String, Object> fields = new HashMap<>(castMap(lead.get("fields")));
        fields.put("_hp", "");
        lead.put("fields", fields);

        postLead(lead).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT fields_json FROM leads", String.class)).doesNotContain("_hp");
    }

    @Test
    void adminEndpointsRequireBearerToken() throws Exception {
        mvc.perform(get("/api/admin/tenants/" + TENANT + "/leads"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("unauthorized"));
        mvc.perform(get("/api/admin/tenants/" + TENANT + "/leads").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/tenants/new-client").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLeadListIsScopedToTenant() throws Exception {
        createTenant("other-client", true);
        postLead(validLead(TENANT)).andExpect(status().isCreated());
        postLead(validLead("other-client")).andExpect(status().isCreated());

        adminGet("/api/admin/tenants/" + TENANT + "/leads")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].fields.full_name").value("Test Parent"));
        adminGet("/api/admin/tenants/other-client/leads")
                .andExpect(jsonPath("$.total").value(1));
        // Same contact in two tenants is two leads, never a cross-tenant duplicate.
        assertThat(count("leads")).isEqualTo(2);
    }

    @Test
    void adminTenantUpsertValidatesInput() throws Exception {
        Map<String, Object> bad = Map.of("displayName", "", "requiredFields", List.of("ok", "not ok"),
                "dedupeWindowHours", 9999);
        mvc.perform(put("/api/admin/tenants/" + TENANT).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.displayName").exists())
                .andExpect(jsonPath("$.fieldErrors.requiredFields").exists())
                .andExpect(jsonPath("$.fieldErrors.dedupeWindowHours").exists());

        adminGet("/api/admin/tenants/" + TENANT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredFields.length()").value(3))
                .andExpect(jsonPath("$.emailField").value("email"));
        adminGet("/api/admin/tenants/missing-client").andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void corsIsClosedWhenNoOriginsConfigured() throws Exception {
        mvc.perform(options("/api/leads")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }
}
