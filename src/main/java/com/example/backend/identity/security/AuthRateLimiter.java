package com.example.backend.identity.security;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.backend.common.exception.RateLimitExceededException;
import com.example.backend.config.AuthRateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;

/** Application policy and identity keys; Bucket4j owns consumption, refills and retry timing. */
@Component
public class AuthRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);
    private final AuthRateLimitProperties properties;
    private final TimeMeter timeMeter;
    private final Cache<Key, Bucket> buckets;

    private record Key(String operation, String scope, String identifier) { }

    @Autowired
    public AuthRateLimiter(AuthRateLimitProperties properties) {
        this(properties, TimeMeter.SYSTEM_NANOTIME);
    }

    AuthRateLimiter(AuthRateLimitProperties properties, TimeMeter timeMeter) {
        this.properties = properties;
        this.timeMeter = timeMeter;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(properties.maxKeys())
                .expireAfterAccess(properties.window())
                .ticker(timeMeter::currentTimeNanos)
                .build();
    }

    public void checkIp(String operation, String address) {
        check(new Key(operation, "ip", address), properties.attemptsPerIp());
    }

    public void checkAccount(String operation, String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        check(new Key(operation, "account", normalized), properties.attemptsPerAccount());
    }

    private void check(Key key, int limit) {
        if (!properties.enabled()) return;
        var probe = bucketFor(key, limit).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) reject(key, probe.getNanosToWaitForRefill());
    }

    /**
     * Serialize cache admission so reaching capacity rejects new identities instead
     * of evicting an active bucket and accidentally granting a fresh budget.
     * Caffeine expires idle entries; Bucket4j serializes consumption within each bucket.
     */
    private synchronized Bucket bucketFor(Key key, int limit) {
        Bucket existing = buckets.getIfPresent(key);
        if (existing != null) return existing;
        buckets.cleanUp();
        if (buckets.estimatedSize() >= properties.maxKeys()) reject(key, properties.window().toNanos());
        Bucket bucket = Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(bandwidth -> bandwidth.capacity(limit).refillIntervally(limit, properties.window()))
                .build();
        buckets.put(key, bucket);
        return bucket;
    }

    private void reject(Key key, long waitNanos) {
        long retryAfter = Math.max(1, (waitNanos + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1));
        log.warn("Authentication rate limit reached operation={} scope={} retryAfterSeconds={}", key.operation(), key.scope(), retryAfter);
        throw new RateLimitExceededException(retryAfter);
    }
}
