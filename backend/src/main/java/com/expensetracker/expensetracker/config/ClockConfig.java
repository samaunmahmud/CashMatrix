package com.expensetracker.expensetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * "Today" depends on the time zone: a payment due on the 1st shouldn't be
 * flagged a day early because the server happens to run in UTC. All date logic
 * goes through this Clock so the zone is set in one place (and tests can fix it).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.reminders.zone:Europe/London}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
