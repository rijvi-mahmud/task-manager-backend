package com.example.backend.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.example.backend.common.exception.RateLimitExceededException;
import com.example.backend.config.AuthRateLimitProperties;

import io.github.bucket4j.TimeMeter;

class AuthRateLimiterTest {
    private final AtomicLong elapsedNanos = new AtomicLong();
    private final TimeMeter timeMeter = new TimeMeter() {
        @Override public long currentTimeNanos() { return elapsedNanos.get(); }
        @Override public boolean isWallClockBased() { return false; }
    };

    private AuthRateLimiter limiter(int keys) {
        return new AuthRateLimiter(new AuthRateLimitProperties(true, Duration.ofMinutes(1), 2, 1, keys), timeMeter);
    }

    @Test void normalizesAccountsAndKeepsOperationsSeparate() {
        var limiter = limiter(100);
        limiter.checkAccount("login", " ADA@example.com ");
        assertThatThrownBy(() -> limiter.checkAccount("login", "ada@example.com"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, ex -> assertThat(ex.retryAfterSeconds()).isEqualTo(60));
        limiter.checkAccount("register", "ada@example.com");
        limiter.checkAccount("login", "bob@example.com");
    }

    @Test void resetsAtTheWindowBoundaryAndRoundsRetryUp() {
        var limiter = limiter(100);
        limiter.checkIp("login", "127.0.0.1");
        limiter.checkIp("login", "127.0.0.1");
        elapsedNanos.set(Duration.ofMillis(59_100).toNanos());
        assertThatThrownBy(() -> limiter.checkIp("login", "127.0.0.1"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, ex -> assertThat(ex.retryAfterSeconds()).isEqualTo(1));
        elapsedNanos.set(Duration.ofSeconds(60).toNanos());
        limiter.checkIp("login", "127.0.0.1");
    }

    @Test void boundsMemoryAndReclaimsExpiredKeys() {
        var limiter = limiter(1);
        limiter.checkIp("login", "one");
        assertThatThrownBy(() -> limiter.checkIp("login", "two")).isInstanceOf(RateLimitExceededException.class);
        elapsedNanos.set(Duration.ofSeconds(60).toNanos());
        limiter.checkIp("login", "two");
    }

    @Test void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new AuthRateLimitProperties(true, Duration.ZERO, 2, 1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsRefillsFasterThanBucket4jSupports() {
        assertThatThrownBy(() -> new AuthRateLimitProperties(true, Duration.ofNanos(1), 2, 1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void bucketConsumptionIsAtomicAcrossConcurrentRequests() {
        var limiter = limiter(100);
        var accepted = new AtomicInteger();
        IntStream.range(0, 20).parallel().forEach(attempt -> {
            try {
                limiter.checkIp("login", "127.0.0.1");
                accepted.incrementAndGet();
            } catch (RateLimitExceededException expected) {
                // Exhausted callers must be rejected even when requests arrive together.
            }
        });
        assertThat(accepted.get()).isEqualTo(2);
    }

    @Test void disabledLimitsDoNotConsumeCapacity() {
        var limiter = new AuthRateLimiter(new AuthRateLimitProperties(false, Duration.ofMinutes(1), 1, 1, 1), timeMeter);
        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.checkIp("login", "address-" + attempt);
        }
    }

}
