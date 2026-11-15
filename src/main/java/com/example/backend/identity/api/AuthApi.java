package com.example.backend.identity.api;

import org.springframework.http.ResponseEntity;

import com.example.backend.common.api.ApiTags;
import com.example.backend.common.api.ProblemResponses;
import com.example.backend.identity.query.CurrentUserView;
import com.example.backend.identity.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** OpenAPI contract for sign-up, sign-in and password reset, implemented by {@link AuthController}. */
@Tag(name = ApiTags.AUTH)
public interface AuthApi {

    @Operation(operationId = "getCsrfToken", summary = "Issue a CSRF token",
            description = "Sets the `XSRF-TOKEN` cookie. Send its value in the `X-XSRF-TOKEN` header on every "
                    + "POST, PUT, PATCH and DELETE. Call this when the cookie is missing, e.g. on first load "
                    + "or right after signing in or out (both rotate the token).")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Cookie set")
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    void csrf(@Parameter(hidden = true) HttpServletRequest request);

    @ApiResponse(responseCode = "429", ref = ProblemResponses.TOO_MANY_REQUESTS_REF)
    @Operation(operationId = "register", summary = "Create an account and sign in",
            description = "No email confirmation. If the email was already added to a workspace, this claims "
                    + "that pending account. A first workspace is created for users who have none.")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Registered and signed in; the session cookie is set",
            headers = @Header(name = "Location", description = "URI of the current-user resource",
                    schema = @Schema(type = "string", format = "uri")),
            content = @Content(schema = @Schema(implementation = CurrentUserView.class)))
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "403", ref = ProblemResponses.FORBIDDEN_REF)
    @ApiResponse(responseCode = "409", ref = ProblemResponses.CONFLICT_REF)
    ResponseEntity<CurrentUserView> register(@Valid RegisterRequest body,
                                             @Parameter(hidden = true) HttpServletRequest request,
                                             @Parameter(hidden = true) HttpServletResponse response);

    @ApiResponse(responseCode = "429", ref = ProblemResponses.TOO_MANY_REQUESTS_REF)
    @Operation(operationId = "login", summary = "Sign in",
            description = "Starts a session (HttpOnly `SESSION` cookie) and rotates the CSRF token.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Signed in")
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "401", ref = ProblemResponses.UNAUTHORIZED_REF)
    @ApiResponse(responseCode = "403", ref = ProblemResponses.FORBIDDEN_REF)
    CurrentUserView login(@Valid LoginRequest body,
                          @Parameter(hidden = true) HttpServletRequest request,
                          @Parameter(hidden = true) HttpServletResponse response);

    @Operation(operationId = "logout", summary = "Sign out", description = "Ends the session and rotates the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Signed out")
    void logout(@Parameter(hidden = true) HttpServletRequest request,
                @Parameter(hidden = true) HttpServletResponse response);

    @Operation(operationId = "getCurrentUser", summary = "The signed-in user",
            description = "`401` means no one is signed in.")
    @ApiResponse(responseCode = "200", description = "The current user")
    CurrentUserView me(@Parameter(hidden = true) AuthenticatedUser user);

    @ApiResponse(responseCode = "429", ref = ProblemResponses.TOO_MANY_REQUESTS_REF)
    @Operation(operationId = "requestPasswordReset", summary = "Request a password reset link",
            description = "Always `202`, whether or not the email has an account, so accounts cannot be "
                    + "discovered. The link is valid for 30 minutes and delivered by the configured sender "
                    + "(the server log in this deployment).")
    @SecurityRequirements
    @ApiResponse(responseCode = "202", description = "If the account exists, a link was sent")
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "403", ref = ProblemResponses.FORBIDDEN_REF)
    void requestPasswordReset(@Valid PasswordResetRequest body);

    @ApiResponse(responseCode = "429", ref = ProblemResponses.TOO_MANY_REQUESTS_REF)
    @Operation(operationId = "resetPassword", summary = "Set a new password from a reset link",
            description = "Single use. Signs the user out of every existing session.")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Password changed; sign in with the new password")
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "403", ref = ProblemResponses.FORBIDDEN_REF)
    void resetPassword(@Valid PasswordResetConfirmRequest body);
}
