package com.example.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs integration tests against a real PostgreSQL in Docker, the same engine
 * as production, so Flyway migrations, constraints and grants are verified for
 * real. {@code @ServiceConnection} points the datasource at the container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String APP_ROLE = "taskmanager_app";
    public static final String APP_ROLE_PASSWORD = "taskmanager_app";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine")
                .withInitScript("db/test-init.sql"); // creates the least-privilege role
    }
}
