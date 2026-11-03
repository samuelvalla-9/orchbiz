package com.orchbiz.leadapi.web;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.orchbiz.leadapi.config.OrchbizProperties;
import com.orchbiz.leadapi.lead.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * In-memory fixed-window limit per client IP for public lead submission. Single-instance only; behind a
 * reverse proxy set FORWARD_HEADERS_STRATEGY so the real client IP is used instead of the proxy's.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED = 10_000;

    private record Window(long start, int count) {
    }

    private final OrchbizProperties.RateLimit config;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitInterceptor(OrchbizProperties properties, Clock clock) {
        this.config = properties.rateLimit();
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!config.enabled() || !"POST".equals(request.getMethod())) {
            return true;
        }
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MS);
        }
        Window w = windows.compute(request.getRemoteAddr(), (ip, cur) ->
                cur == null || now - cur.start() >= WINDOW_MS ? new Window(now, 1) : new Window(cur.start(), cur.count() + 1));
        if (w.count() > config.requestsPerMinute()) {
            long retryAfter = Math.max(1, (w.start() + WINDOW_MS - now + 999) / 1000);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited");
        }
        return true;
    }

    /** Clears all windows (used by tests). */
    public void reset() {
        windows.clear();
    }
}
