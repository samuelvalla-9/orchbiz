package com.orchbiz.leadapi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.orchbiz.leadapi.events.EventDispatcher;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "orchbiz.events", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    private final EventDispatcher dispatcher;

    public SchedulingConfig(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${orchbiz.events.dispatch-interval-ms:5000}")
    public void dispatchEvents() {
        dispatcher.dispatchPending();
    }
}
