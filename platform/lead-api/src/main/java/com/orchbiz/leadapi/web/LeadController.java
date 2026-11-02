package com.orchbiz.leadapi.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.orchbiz.leadapi.lead.LeadService;
import com.orchbiz.leadapi.lead.LeadSubmission;

@RestController
public class LeadController {

    private final LeadService service;

    public LeadController(LeadService service) {
        this.service = service;
    }

    /**
     * 201 new lead, 200 duplicate within the tenant's dedupe window, 202 accepted-but-discarded (spam trap),
     * 400 validation error, 403 unknown or inactive tenant.
     */
    @PostMapping(path = "/api/leads", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody LeadSubmission submission) {
        LeadService.Result r = service.submit(submission);
        if (r.discarded()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("accepted", true));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("leadId", r.leadId().toString());
        body.put("tenantId", r.tenantId());
        body.put("duplicate", r.duplicate());
        body.put("storedIn", List.of("postgresql"));
        return ResponseEntity.status(r.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(body);
    }
}
