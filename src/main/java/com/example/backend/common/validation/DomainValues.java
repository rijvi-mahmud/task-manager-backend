package com.example.backend.common.validation;

import com.example.backend.common.exception.DomainValidationException;
import java.time.LocalDate;
/** Value rules shared by aggregates and queries, including callers outside HTTP. */
public final class DomainValues {
    private DomainValues() { }

    public static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " must not be blank");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new DomainValidationException(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    public static String optionalText(String value, String field, int maxLength) {
        return value == null || value.isBlank() ? null : requiredText(value, field, maxLength);
    }

    public static LocalDate optionalDate(LocalDate value, String field) {
        if (value != null && (value.getYear() < 1 || value.getYear() > 9999)) {
            throw new DomainValidationException(field + " must be between 0001-01-01 and 9999-12-31");
        }
        return value;
    }

}
