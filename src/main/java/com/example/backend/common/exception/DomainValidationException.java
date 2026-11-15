package com.example.backend.common.exception;

/**
 * A request broke a business rule (blank title, unknown assignee, ...).
 *
 * <p>Mapped to 400. Deliberately not {@link IllegalArgumentException}: that is
 * thrown by the JDK and libraries for programming errors too, which must stay
 * 500s and must not leak their messages to API clients.
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
