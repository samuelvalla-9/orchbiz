package com.orchbiz.leadapi.lead;

import java.util.Map;

/** JSON body POSTed by the enquiry-form module (see catalogue/websites/enquiry-form outputSchema). */
public record LeadSubmission(
        String tenantId,
        String submittedAt,
        String sourcePage,
        Map<String, Object> fields) {
}
