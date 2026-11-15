package com.example.backend.identity.domain;

/** Published after a password reset so existing sessions can be revoked. */
public record PasswordChangedEvent(Long userId, String email) {
}
