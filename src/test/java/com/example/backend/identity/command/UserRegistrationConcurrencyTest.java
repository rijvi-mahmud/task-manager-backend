package com.example.backend.identity.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.backend.TestcontainersConfiguration;
import com.example.backend.common.exception.ConflictException;
import com.example.backend.identity.domain.UserRepository;

/** A second claim must wait for the first transaction and then observe its committed password. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserRegistrationConcurrencyTest {
    @Autowired UserCommandService commands;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @Test void onlyOneTransactionCanClaimAnInvitedAccount() throws Exception {
        String email = "claim-" + UUID.randomUUID() + "@test.example";
        Long id = commands.findOrInvite(email);
        String firstHash = passwords.encode("first-password");
        var firstHasLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var user = users.findByEmailForUpdate(email).orElseThrow();
                firstHasLock.countDown();
                awaitLatch(releaseFirst);
                user.claim("First registrant", firstHash);
            }));
            assertThat(firstHasLock.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                try {
                    commands.handle(new RegisterUserCommand(email, "Second registrant", "second-password"));
                    return false;
                } catch (ConflictException expected) {
                    return true;
                }
            });
            // Observe an actual database lock wait, rather than relying on password-hashing timing.
            await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query ilike '%users%' and pid <> pg_backend_pid()
                    """, Long.class) > 0);
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).as("the second claim conflicts").isTrue();
            var saved = users.findById(id).orElseThrow();
            assertThat(saved.getDisplayName()).isEqualTo("First registrant");
            assertThat(passwords.matches("first-password", saved.getPasswordHash())).isTrue();
            assertThat(passwords.matches("second-password", saved.getPasswordHash())).isFalse();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting to release the claim");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Claim interrupted", interrupted);
        }
    }
}
