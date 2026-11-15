package com.example.backend.common.api;

import com.example.backend.config.OpenApiConfig;

/**
 * References to the shared error responses declared in {@link OpenApiConfig},
 * for use in {@code @ApiResponse(ref = ...)}.
 */
public final class ProblemResponses {

    public static final String BAD_REQUEST = "BadRequest";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String FORBIDDEN = "Forbidden";
    public static final String NOT_FOUND = "NotFound";
    public static final String CONFLICT = "Conflict";
    public static final String TOO_MANY_REQUESTS = "TooManyRequests";

    public static final String INTERNAL_SERVER_ERROR = "InternalServerError";

    private static final String PREFIX = "#/components/responses/";

    public static final String BAD_REQUEST_REF = PREFIX + BAD_REQUEST;
    public static final String UNAUTHORIZED_REF = PREFIX + UNAUTHORIZED;
    public static final String FORBIDDEN_REF = PREFIX + FORBIDDEN;
    public static final String NOT_FOUND_REF = PREFIX + NOT_FOUND;
    public static final String CONFLICT_REF = PREFIX + CONFLICT;
    public static final String TOO_MANY_REQUESTS_REF = PREFIX + TOO_MANY_REQUESTS;

    public static final String INTERNAL_SERVER_ERROR_REF = PREFIX + INTERNAL_SERVER_ERROR;

    private ProblemResponses() {
    }
}
