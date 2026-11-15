package com.example.backend.common.api;

/** Successful JSON responses share this envelope; HTTP status retains its meaning. */
public record ApiResponse<T>(T data, ResponseMetadata meta) { }
