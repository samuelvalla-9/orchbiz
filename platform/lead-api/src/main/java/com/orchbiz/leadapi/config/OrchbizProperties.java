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
        @DefaultValue Events events) {

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
            return webhookUrl != null && !webhookUrl.isBlank();
        }
    }
}
