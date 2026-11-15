package com.example.backend.identity.command;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import com.example.backend.common.observability.AuditEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.backend.identity.security.AuthRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.backend.common.exception.ConflictException;
import com.example.backend.common.exception.DomainValidationException;
import com.example.backend.common.validation.Emails;
import com.example.backend.identity.domain.PasswordChangedEvent;
import com.example.backend.identity.domain.PasswordResetToken;
import com.example.backend.identity.domain.PasswordResetTokenRepository;
import com.example.backend.identity.domain.User;
import com.example.backend.identity.domain.UserRegisteredEvent;
import com.example.backend.identity.domain.UserRepository;

/** Account lifecycle: registration, invitations and password resets. */
@Service
@Transactional
public class UserCommandService {
    private static final Logger log = LoggerFactory.getLogger(UserCommandService.class);
    private final AuthRateLimiter rateLimiter;

    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final int RESET_TOKEN_BYTES = 32;

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetLinkSender linkSender;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final URI frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public UserCommandService(UserRepository users, PasswordResetTokenRepository resetTokens,
                              PasswordEncoder passwordEncoder, PasswordResetLinkSender linkSender,
                              ApplicationEventPublisher events, Clock clock,
                              @Value("${app.frontend-url}") URI frontendUrl, AuthRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.linkSender = linkSender;
        this.events = events;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Creates an account, or claims a pending one (someone already added this
     * email to a workspace), keeping its memberships.
     *
     * @return id of the registered user
     */
    public Long handle(RegisterUserCommand command) {
        String email = Emails.normalize(command.email());
        rateLimiter.checkAccount("register", email);
        String hash = passwordEncoder.encode(PasswordPolicy.validate(command.password()));
        User user = users.findByEmailForUpdate(email)
                .map(existing -> {
                    existing.claim(command.displayName(), hash);
                    return existing;
                })
                .orElseGet(() -> users.save(User.registered(email, command.displayName(), hash)));
        events.publishEvent(new UserRegisteredEvent(user.getId(), user.getDisplayName()));
        events.publishEvent(new AuditEvent("account.registered", user.getId(), null, user.getId()));
        return user.getId();
    }

    /**
     * Finds a user by email, or creates a pending account so the person can be
     * added to a workspace before they register.
     *
     * @return id of the existing or newly invited user
     */
    public Long findOrInvite(String email) {
        String normalized = Emails.normalize(email);
        return users.findByEmail(normalized)
                .orElseGet(() -> users.save(User.pending(normalized)))
                .getId();
    }

    /**
     * Issues a reset link if the email belongs to a registered account. Callers
     * get the same answer either way, so the endpoint does not reveal which
     * emails have accounts.
     */
    public void handle(RequestPasswordResetCommand command) {
        String email;
        try {
            email = Emails.normalize(command.email());
        } catch (DomainValidationException ex) {
            return;
        }
        rateLimiter.checkAccount("password-reset", email);
        log.info("Password reset request accepted");
        users.findByEmail(email).filter(User::isRegistered).ifPresent(user -> {
            resetTokens.deleteAllForUser(user.getId());
            String token = newToken();
            resetTokens.save(new PasswordResetToken(user.getId(), sha256(token), Instant.now(clock).plus(RESET_TOKEN_TTL)));
            linkSender.send(user.getEmail(), UriComponentsBuilder.fromUri(frontendUrl)
                    .path("/reset-password").queryParam("token", token).build().toUri());
        });
    }

    /** Sets a new password from a valid link, then invalidates all links and signs the user out everywhere. */
    public void handle(ResetPasswordCommand command) {
        String hash = passwordEncoder.encode(PasswordPolicy.validate(command.newPassword()));
        Instant now = Instant.now(clock);
        PasswordResetToken token = command.token() == null ? null
                : resetTokens.findByTokenHash(sha256(command.token())).filter(t -> t.isUsable(now)).orElse(null);
        if (token == null) {
            throw new DomainValidationException("This reset link is invalid or has expired. Request a new one.");
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new ConflictException("The account no longer exists"));
        user.changePassword(hash);
        token.markUsed(now);
        resetTokens.deleteAllForUser(user.getId());
        events.publishEvent(new PasswordChangedEvent(user.getId(), user.getEmail()));
        events.publishEvent(new AuditEvent("password.reset", user.getId(), null, user.getId()));
    }

    private String newToken() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
        }
    }
}
