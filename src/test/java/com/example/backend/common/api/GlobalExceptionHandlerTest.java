package com.example.backend.common.api;

import com.example.backend.common.exception.DomainValidationException;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void uniqueAndForeignKeyViolationsAreConflicts() {
        assertThat(handler.handleDataIntegrity(violation("23505")).getStatus()).isEqualTo(409);
        assertThat(handler.handleDataIntegrity(violation("23503")).getStatus()).isEqualTo(409);
    }

    @Test
    void checkAndNotNullViolationsAreBadRequests() {
        assertThat(handler.handleDataIntegrity(violation("23514")).getStatus()).isEqualTo(400);
        assertThat(handler.handleDataIntegrity(violation("23502")).getStatus()).isEqualTo(400);
    }

    @Test
    void domainValidationIsBadRequestButLibraryIllegalArgumentIsNot() {
        assertThat(handler.handleDomainValidation(new DomainValidationException("Title must not be blank")))
                .satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo(400);
                    assertThat(p.getDetail()).isEqualTo("Title must not be blank");
                });
        assertThat(handler.handleUnexpected(new IllegalArgumentException("internal detail")))
                .satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo(500);
                    assertThat(p.getDetail()).doesNotContain("internal detail");
                });
    }

    @Test
    void unknownIntegrityFailuresAreServerErrors() {
        assertThat(handler.handleDataIntegrity(violation("22001")).getStatus()).isEqualTo(500);
        assertThat(handler.handleDataIntegrity(new DataIntegrityViolationException("internal detail")).getDetail())
                .doesNotContain("internal detail");
    }

    private static DataIntegrityViolationException violation(String sqlState) {
        return new DataIntegrityViolationException("constraint", new SQLException("violated", sqlState));
    }
}
