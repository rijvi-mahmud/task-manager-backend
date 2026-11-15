package com.example.backend.common.exception;

/** The request conflicts with existing state (e.g. a duplicate unique value). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
