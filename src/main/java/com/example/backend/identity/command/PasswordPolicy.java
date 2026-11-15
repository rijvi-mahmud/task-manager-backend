package com.example.backend.identity.command;

import java.nio.charset.StandardCharsets;

import com.example.backend.common.exception.DomainValidationException;

/**
 * Password rules (NIST SP 800-63B style: length over composition rules).
 * The 72-byte cap is bcrypt's input limit; longer input would be truncated or rejected.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static String validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new DomainValidationException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new DomainValidationException("Password must be at most " + MAX_BYTES + " bytes");
        }
        if (password.isBlank()) {
            throw new DomainValidationException("Password must not be blank");
        }
        return password;
    }
}
