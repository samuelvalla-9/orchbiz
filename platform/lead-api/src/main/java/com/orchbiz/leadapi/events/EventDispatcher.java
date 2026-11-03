package com.orchbiz.leadapi.events;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.orchbiz.leadapi.config.OrchbizProperties;

/**
 * Delivers PENDING outbox events to the configured n8n webhook. Designed for a single API instance;
 * receivers should de-duplicate on eventId because delivery is at-least-once.
 */
@Component
public class EventDispatcher {

    public static final String TOKEN_HEADER = "X-OrchBiz-Token";
    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);
    private static final int BATCH = 20;

    private final EventRepository events;
    private final OrchbizProperties.Events config;
    private final Clock clock;
    private final HttpClient http;

    public EventDispatcher(EventRepository events, OrchbizProperties properties, Clock clock) {
        this.events = events;
        this.config = properties.events();
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** @return number of events delivered in this pass */
    public synchronized int dispatchPending() {
        if (!config.webhookConfigured()) {
            return 0;
        }
        List<EventRepository.EventRow> pending = events.findPending(BATCH);
        List<String> targets = config.webhookUrls();
        int sent = 0;
        for (EventRepository.EventRow e : pending) {
            int attempts = e.attempts() + 1;
            List<String> problems = new ArrayList<>();
            boolean interrupted = false;
            // Every target receives every retry; receivers de-duplicate on eventId.
            for (int t = 0; t < targets.size(); t++) {
                String prefix = targets.size() == 1 ? "" : "target " + (t + 1) + ": ";
                try {
                    HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(targets.get(t)))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .header("X-OrchBiz-Event", e.eventType())
                            .header("X-OrchBiz-Event-Id", e.id().toString())
                            .POST(HttpRequest.BodyPublishers.ofString(e.payloadJson()));
                    if (config.webhookToken() != null && !config.webhookToken().isBlank()) {
                        req.header(TOKEN_HEADER, config.webhookToken());
                    }
                    HttpResponse<Void> res = http.send(req.build(), HttpResponse.BodyHandlers.discarding());
                    if (res.statusCode() / 100 != 2) {
                        problems.add(prefix + "HTTP " + res.statusCode());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    problems.add(prefix + "interrupted");
                    interrupted = true;
                    break;
                } catch (Exception ex) {
                    problems.add(prefix + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }
            if (problems.isEmpty()) {
                events.markSent(e.id(), attempts, OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));
                sent++;
            } else {
                fail(e, attempts, String.join("; ", problems));
            }
            if (interrupted) {
                break;
            }
        }
        return sent;
    }

    private void fail(EventRepository.EventRow e, int attempts, String error) {
        boolean terminal = attempts >= config.maxAttempts();
        events.markAttemptFailed(e.id(), attempts, error, terminal);
        log.warn("Event {} ({}) for tenant {} delivery attempt {} failed: {}{}", e.id(), e.eventType(), e.tenantId(),
                attempts, error, terminal ? " - giving up" : "");
    }
}
