package com.orchbiz.leadapi.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "orchbiz")
public record OrchbizProperties(
        String adminToken,
        @DefaultValue("_hp") String honeypotField,
        @DefaultValue Cors cors,
        @DefaultValue Events events,
        @DefaultValue RateLimit rateLimit) {

    /** Per client IP, fixed one-minute window on POST /api/leads. 0 disables the limit. */
    public record RateLimit(@DefaultValue("30") int requestsPerMinute) {
        public boolean enabled() {
            return requestsPerMinute > 0;
        }
    }

    public boolean adminEnabled() {
        return adminToken != null && !adminToken.isBlank();
    }

    public record Cors(@DefaultValue("") String allowedOrigins) {
        public List<String> originList() {
            return Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    public record Events(
            String webhookUrl,
            String webhookToken,
            @DefaultValue("5000") long dispatchIntervalMs,
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("true") boolean schedulingEnabled) {

        public boolean webhookConfigured() {
            return !webhookUrls().isEmpty();
        }

        /** Comma-separated targets; an event is SENT only when every target returns 2xx. */
        public List<String> webhookUrls() {
            if (webhookUrl == null) {
                return List.of();
            }
            return Arrays.stream(webhookUrl.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }
}
