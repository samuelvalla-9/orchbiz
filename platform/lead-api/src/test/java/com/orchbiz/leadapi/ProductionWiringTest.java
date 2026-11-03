package com.orchbiz.leadapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Starts the context with the production Clock bean (no test override) to catch wiring cycles. */
@SpringBootTest
@ActiveProfiles("test")
class ProductionWiringTest {

    @Autowired Clock clock;

    @Test
    void contextStartsWithSystemClock() {
        assertThat(clock).isEqualTo(Clock.systemUTC());
    }
}
