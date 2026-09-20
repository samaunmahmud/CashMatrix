package com.expensetracker.expensetracker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Pins "today" to 2026-09-20 so date-based assertions never depend on the real date. */
@TestConfiguration
public class FixedClockTestConfig {

    public static final String TODAY = "2026-09-20";

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(Instant.parse(TODAY + "T09:00:00Z"), ZoneId.of("Europe/London"));
    }
}
