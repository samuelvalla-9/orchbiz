package com.orchbiz.leadapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractApiTest.ClockConfig.class)
abstract class AbstractApiTest {

    static final String ADMIN = "Bearer test-admin-value";
    static final String TENANT = "apex-coaching";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired com.orchbiz.leadapi.web.RateLimitInterceptor rateLimiter;

    @BeforeEach
    void cleanDatabase() throws Exception {
        jdbc.update("DELETE FROM follow_up_drafts");
        jdbc.update("DELETE FROM lead_events");
        jdbc.update("DELETE FROM leads");
        jdbc.update("DELETE FROM tenants");
        clock.reset();
        rateLimiter.reset();
        createTenant(TENANT, true);
    }

    void createTenant(String id, boolean active) throws Exception {
        Map<String, Object> body = Map.of(
                "displayName", "Apex Horizon Coaching",
                "requiredFields", List.of("full_name", "phone", "course_interest"),
                "dedupeWindowHours", 24,
                "active", active);
        mvc.perform(put("/api/admin/tenants/" + id).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    static Map<String, Object> validLead(String tenantId) {
        return Map.of(
                "tenantId", tenantId,
                "submittedAt", "2026-11-02T15:30:00+05:30",
                "sourcePage", "/contact",
                "fields", Map.of(
                        "full_name", "Test Parent",
                        "phone", "+91 98765-43210",
                        "email", "Parent@Example.com",
                        "course_interest", "JEE Foundation",
                        "message", "Please call after 6pm"));
    }

    ResultActions postLead(Object body) throws Exception {
        return mvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json.writeValueAsString(body)));
    }

    String createLead(String tenantId) throws Exception {
        String body = postLead(validLead(tenantId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("leadId").asText();
    }

    ResultActions adminGet(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", ADMIN));
    }

    int count(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
