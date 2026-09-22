package com.expensetracker.expensetracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Small schema changes that Hibernate's automatic update won't make on a database that
 * already exists (it adds new tables and columns but never relaxes a column).
 *
 * Each statement is safe to run on every start. Runs before the reminder job, which
 * starts once the application is ready.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaUpgrades implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) return; // tests build a fresh schema, so there is nothing to upgrade

        // Budget alerts aren't tied to a calendar item, so a notification's event became optional.
        apply("ALTER TABLE notifications ALTER COLUMN event_id DROP NOT NULL");
    }

    private void apply(String sql) {
        try {
            jdbc.execute(sql);
        } catch (RuntimeException ex) {
            log.warn("Schema upgrade failed ({}): {}", sql, ex.getMessage());
        }
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException ex) {
            return false;
        }
    }
}
