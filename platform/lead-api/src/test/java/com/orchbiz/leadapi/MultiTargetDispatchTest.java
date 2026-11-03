package com.orchbiz.leadapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.orchbiz.leadapi.events.EventDispatcher;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** One event fanned out to notify-business-owner and classify-enquiry webhooks. */
class MultiTargetDispatchTest extends AbstractApiTest {

    static final HttpServer SERVER;
    static final List<String> HITS = new CopyOnWriteArrayList<>();
    static final AtomicInteger SECOND_CODE = new AtomicInteger(200);

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SERVER.createContext("/notify", ex -> respond(ex, "notify", 200));
        SERVER.createContext("/classify", ex -> respond(ex, "classify", SECOND_CODE.get()));
        SERVER.start();
    }

    private static void respond(HttpExchange ex, String name, int code) throws IOException {
        try (var in = ex.getRequestBody()) {
            in.readAllBytes();
        }
        HITS.add(name);
        ex.sendResponseHeaders(code, -1);
        ex.close();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        r.add("orchbiz.events.webhook-url", () -> base + "/notify, " + base + "/classify");
    }

    @AfterAll
    static void stop() {
        SERVER.stop(0);
    }

    @Autowired EventDispatcher dispatcher;

    @Test
    void eventIsSentOnlyWhenEveryTargetAccepts() throws Exception {
        HITS.clear();
        createLead(TENANT);
        SECOND_CODE.set(502);
        assertThat(dispatcher.dispatchPending()).isZero();
        assertThat(jdbc.queryForObject("SELECT last_error FROM lead_events", String.class)).isEqualTo("target 2: HTTP 502");

        SECOND_CODE.set(200);
        assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        // The first target sees the event twice and must de-duplicate on eventId.
        assertThat(HITS).containsExactly("notify", "classify", "notify", "classify");
        assertThat(jdbc.queryForObject("SELECT status FROM lead_events", String.class)).isEqualTo("SENT");
    }
}
