package com.example.backend.common.exception;

/**
 * The caller is signed in and may see the resource, but this action needs a
 * stronger permission (e.g. only workspace owners can remove members). Mapped to 403.
 *
 * <p>Resources the caller may not see at all are reported as
 * {@link ResourceNotFoundException} instead, so their existence is not revealed.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
