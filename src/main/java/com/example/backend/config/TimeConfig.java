package com.example.backend.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * The application's single source of time.
 *
 * <ul>
 *   <li>Every timestamp ({@code createdAt}, {@code updatedAt}, bulk updates) and
 *       every "today" (overdue) comes from this {@link Clock}, so tests can fix it.</li>
 *   <li>The zone is the team's business time zone ({@code APP_TIME_ZONE}), not the
 *       server's: a due date passes at midnight where the team works, wherever the
 *       server runs. Instants are stored in UTC regardless.</li>
 * </ul>
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = TimeConfig.DATE_TIME_PROVIDER)
public class TimeConfig {

    static final String DATE_TIME_PROVIDER = "clockDateTimeProvider";

    @Bean
    public Clock clock(@Value("${app.time-zone}") ZoneId zone) {
        return Clock.system(zone);
    }

    /** Feeds JPA auditing ({@code @CreatedDate}, {@code @LastModifiedDate}) from the Clock bean. */
    @Bean(DATE_TIME_PROVIDER)
    public DateTimeProvider clockDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
