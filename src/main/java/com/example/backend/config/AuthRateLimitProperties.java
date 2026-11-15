package com.example.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.auth.rate-limit")
public record AuthRateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1m") Duration window,
        @DefaultValue("60") int attemptsPerIp,
        @DefaultValue("10") int attemptsPerAccount,
        @DefaultValue("10000") int maxKeys) {
    public AuthRateLimitProperties {
        if (window == null || window.isNegative() || window.isZero() || window.compareTo(Duration.ofDays(1)) > 0
                || attemptsPerIp < 1 || attemptsPerAccount < 1 || maxKeys < 1
                || window.toNanos() < Math.max(attemptsPerIp, attemptsPerAccount)) {
            throw new IllegalArgumentException("Authentication limits must be positive, with a window at most one day and at least one nanosecond per token");
        }
    }
}
