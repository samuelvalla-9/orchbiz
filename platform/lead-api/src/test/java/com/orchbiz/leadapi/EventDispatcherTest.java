package com.orchbiz.leadapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.orchbiz.leadapi.events.EventDispatcher;
import com.sun.net.httpserver.HttpServer;

/** Delivery of lead.created events to a stand-in n8n webhook. */
class EventDispatcherTest extends AbstractApiTest {

    record Received(String token, String eventId, String body) {
    }

    static final HttpServer WEBHOOK;
    static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    static final AtomicInteger RESPONSE_CODE = new AtomicInteger(200);

    static {
        try {
            WEBHOOK = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        WEBHOOK.createContext("/webhook/lead-events", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                RECEIVED.add(new Received(
                        exchange.getRequestHeaders().getFirst(EventDispatcher.TOKEN_HEADER),
                        exchange.getRequestHeaders().getFirst("X-OrchBiz-Event-Id"),
                        new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
            exchange.sendResponseHeaders(RESPONSE_CODE.get(), -1);
            exchange.close();
        });
        WEBHOOK.start();
    }

    @DynamicPropertySource
    static void webhookProperties(DynamicPropertyRegistry registry) {
        registry.add("orchbiz.events.webhook-url",
                () -> "http://127.0.0.1:" + WEBHOOK.getAddress().getPort() + "/webhook/lead-events");
        registry.add("orchbiz.events.webhook-token", () -> "test-webhook-value");
        registry.add("orchbiz.events.max-attempts", () -> "3");
    }

    @AfterAll
    static void stopServer() {
        WEBHOOK.stop(0);
    }

    @Autowired EventDispatcher dispatcher;

    @BeforeEach
    void resetWebhook() {
        RECEIVED.clear();
        RESPONSE_CODE.set(200);
    }

    @Test
    void pendingEventIsDeliveredOnceWithTokenAndMarkedSent() throws Exception {
        postLead(validLead(TENANT)).andExpect(status().isCreated());

        assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(RECEIVED).hasSize(1);
        Received r = RECEIVED.get(0);
        assertThat(r.token()).isEqualTo("test-webhook-value");
        JsonNode payload = json.readTree(r.body());
        assertThat(payload.get("event").asText()).isEqualTo("lead.created");
        assertThat(payload.get("eventId").asText()).isEqualTo(r.eventId());
        assertThat(payload.get("tenantId").asText()).isEqualTo(TENANT);
        assertThat(payload.at("/lead/fields/full_name").asText()).isEqualTo("Test Parent");

        Map<String, Object> row = jdbc.queryForMap("SELECT status, attempts, sent_at FROM lead_events");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(1);
        assertThat(row.get("sent_at")).isNotNull();

        assertThat(dispatcher.dispatchPending()).isZero();
        assertThat(RECEIVED).hasSize(1);
    }

    @Test
    void failedDeliveryIsRetriedThenMarkedFailed() throws Exception {
        postLead(validLead(TENANT)).andExpect(status().isCreated());
        RESPONSE_CODE.set(500);

        dispatcher.dispatchPending();
        Map<String, Object> row = jdbc.queryForMap("SELECT status, attempts, last_error FROM lead_events");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(1);
        assertThat((String) row.get("last_error")).isEqualTo("HTTP 500");

        dispatcher.dispatchPending();
        dispatcher.dispatchPending();
        row = jdbc.queryForMap("SELECT status, attempts FROM lead_events");
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(3);

        dispatcher.dispatchPending();
        assertThat(RECEIVED).hasSize(3);
    }

    @Test
    void recoveryAfterTransientFailure() throws Exception {
        postLead(validLead(TENANT)).andExpect(status().isCreated());
        RESPONSE_CODE.set(503);
        dispatcher.dispatchPending();
        RESPONSE_CODE.set(204);
        assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM lead_events", String.class)).isEqualTo("SENT");
    }
}
