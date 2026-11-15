package com.example.backend.common.validation;

import java.util.Locale;
import java.util.regex.Pattern;

import com.example.backend.common.exception.DomainValidationException;

/** Email normalization shared by every module that looks users up by email. */
public final class Emails {

    public static final int MAX_LENGTH = 120;

    // Matches the database's basic email invariant; HTTP also applies @Email.
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    private Emails() {
    }

    /** Emails are compared case-insensitively, so they are stored lower-cased. */
    public static String normalize(String email) {
        String normalized = DomainValues.requiredText(email, "Email", MAX_LENGTH).toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(normalized).matches()) {
            throw new DomainValidationException("Email must be valid");
        }
        return normalized;
    }
}
