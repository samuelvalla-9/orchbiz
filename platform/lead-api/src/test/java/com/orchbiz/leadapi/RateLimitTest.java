package com.orchbiz.leadapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "orchbiz.rate-limit.requests-per-minute=3")
class RateLimitTest extends AbstractApiTest {

    private org.springframework.test.web.servlet.ResultActions postFrom(String ip) throws Exception {
        return mvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(validLead(TENANT)))
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    @Test
    void fourthRequestInAMinuteFromSameIpIsRejected() throws Exception {
        for (int i = 0; i < 3; i++) {
            postFrom("203.0.113.7").andExpect(status().is2xxSuccessful());
        }
        postFrom("203.0.113.7")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("rate_limited"))
                .andExpect(header().exists("Retry-After"));

        // Other clients are unaffected, and the window resets after a minute.
        postFrom("198.51.100.9").andExpect(status().is2xxSuccessful());
        clock.advance(Duration.ofSeconds(61));
        postFrom("203.0.113.7").andExpect(status().is2xxSuccessful());
    }

    @Test
    void adminEndpointsAreNotRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            adminGet("/api/admin/tenants/" + TENANT).andExpect(status().isOk());
        }
    }
}
