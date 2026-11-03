package com.orchbiz.leadapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Write-back endpoints used by the classify-enquiry (CLE-*) and draft-follow-up (DFU-*) workflows. */
class AutomationApiTest extends AbstractApiTest {

    private ResultActions putClassification(String tenant, String leadId, Object body, String auth) throws Exception {
        var req = put("/api/admin/tenants/" + tenant + "/leads/" + leadId + "/classification")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(auth == null ? req : req.header("Authorization", auth));
    }

    private ResultActions postDraft(String tenant, String leadId, Object body) throws Exception {
        return mvc.perform(post("/api/admin/tenants/" + tenant + "/leads/" + leadId + "/drafts")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void cle01_validClassificationIsStoredAndListed() throws Exception {
        String leadId = createLead(TENANT);
        putClassification(TENANT, leadId, Map.of("status", "classified", "intent", "demo-request", "urgency", "high",
                "serviceInterest", "JEE Foundation", "confidence", 0.91), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("demo-request"));

        adminGet("/api/admin/tenants/" + TENANT + "/leads")
                .andExpect(jsonPath("$.items[0].classificationStatus").value("classified"))
                .andExpect(jsonPath("$.items[0].classification.serviceInterest").value("JEE Foundation"))
                .andExpect(jsonPath("$.items[0].notificationStatus").value("PENDING"));
    }

    @Test
    void cle02_labelOutsideFixedSetIsRejected() throws Exception {
        String leadId = createLead(TENANT);
        putClassification(TENANT, leadId, Map.of("status", "classified", "intent", "wants-a-discount", "urgency", "asap",
                "serviceInterest", "x", "confidence", 1.7), ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.intent").exists())
                .andExpect(jsonPath("$.fieldErrors.urgency").exists())
                .andExpect(jsonPath("$.fieldErrors.confidence").exists());
        assertThat(jdbc.queryForObject("SELECT classification_status FROM leads", String.class)).isNull();
    }

    @Test
    void cle03_pendingClassificationNeedsNoLabels() throws Exception {
        String leadId = createLead(TENANT);
        putClassification(TENANT, leadId, Map.of("status", "pending"), ADMIN).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT classification_status FROM leads", String.class)).isEqualTo("pending");
    }

    @Test
    void classificationRequiresAdminAndTenantScope() throws Exception {
        String leadId = createLead(TENANT);
        putClassification(TENANT, leadId, Map.of("status", "pending"), null).andExpect(status().isUnauthorized());

        createTenant("other-client", true);
        putClassification("other-client", leadId, Map.of("status", "pending"), ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("lead_not_found"));
        putClassification(TENANT, "not-a-uuid", Map.of("status", "pending"), ADMIN)
                .andExpect(status().isBadRequest());
    }

    @Test
    void dfu01_draftIsAlwaysStoredAwaitingApproval() throws Exception {
        String leadId = createLead(TENANT);
        Map<String, Object> draft = new HashMap<>(Map.of("channel", "email", "subject", "Your demo class",
                "body", "Hello Test Parent, thanks for your interest.", "source", "template"));
        draft.put("status", "approved");
        postDraft(TENANT, leadId, draft)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("awaiting-approval"))
                .andExpect(jsonPath("$.draftId").exists());

        adminGet("/api/admin/tenants/" + TENANT + "/leads/" + leadId + "/drafts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("awaiting-approval"));
    }

    @Test
    void invalidDraftIsRejected() throws Exception {
        String leadId = createLead(TENANT);
        postDraft(TENANT, leadId, Map.of("channel", "sms", "body", " ", "source", "magic"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.channel").exists())
                .andExpect(jsonPath("$.fieldErrors.body").exists())
                .andExpect(jsonPath("$.fieldErrors.source").exists());
        assertThat(count("follow_up_drafts")).isZero();
    }
}
