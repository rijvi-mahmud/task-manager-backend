package com.example.backend.common.api;

/**
 * Single source of truth for the public API's URI layout and versions.
 *
 * <p>The version is the second path segment ({@code /api/v1/...}). Spring's
 * native API versioning resolves it, routes each request to the handler
 * declared for that version and rejects unsupported versions with a 400
 * problem response. A breaking change ships as a new version (e.g. {@code "v2"})
 * on the affected handlers while {@code v1} keeps working until it is retired.
 */
public final class ApiVersions {

    /** Base path for versioned endpoints; {@code {version}} is resolved by Spring. */
    public static final String BASE = "/api/{version}";

    public static final String V1 = "v1";

    private ApiVersions() {
    }
}
