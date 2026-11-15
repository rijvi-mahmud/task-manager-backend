package com.example.backend.identity.domain;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.example.backend.common.exception.ConflictException;
import com.example.backend.common.validation.DomainValues;
import com.example.backend.common.validation.Emails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An account. A user without a password is <em>pending</em>: someone added them
 * to a workspace by email before they registered. Registering with that email
 * claims the account, keeping the memberships.
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    public static final int DISPLAY_NAME_MAX = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = Emails.MAX_LENGTH)
    private String email;

    @Column(nullable = false, length = DISPLAY_NAME_MAX)
    private String displayName;

    /** Encoded by the PasswordEncoder; never the raw password. Null while pending. */
    private String passwordHash;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    /** Required by JPA. */
    protected User() {
    }

    private User(String email, String displayName, String passwordHash) {
        this.email = Emails.normalize(email);
        rename(displayName);
        this.passwordHash = passwordHash;
    }

    public static User registered(String email, String displayName, String passwordHash) {
        return new User(email, displayName, DomainValues.requiredText(passwordHash, "Password hash", 255));
    }

    /** An invited user: can be added to workspaces and assigned tasks, but cannot sign in yet. */
    public static User pending(String email) {
        String normalized = Emails.normalize(email);
        return new User(normalized, defaultDisplayName(normalized), null);
    }

    /** Completes registration of a pending account. */
    public void claim(String displayName, String passwordHash) {
        if (isRegistered()) {
            throw new ConflictException("An account with this email already exists");
        }
        rename(displayName);
        changePassword(passwordHash);
    }

    public void rename(String displayName) {
        this.displayName = DomainValues.requiredText(displayName, "Display name", DISPLAY_NAME_MAX);
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = DomainValues.requiredText(passwordHash, "Password hash", 255);
    }

    public boolean isRegistered() {
        return passwordHash != null;
    }

    private static String defaultDisplayName(String email) {
        String local = email.substring(0, email.indexOf('@'));
        return local.length() > DISPLAY_NAME_MAX ? local.substring(0, DISPLAY_NAME_MAX) : local;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
