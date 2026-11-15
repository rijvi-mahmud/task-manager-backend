package com.example.backend.identity.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.backend.common.api.ApiVersions;
import com.example.backend.identity.command.RegisterUserCommand;
import com.example.backend.identity.command.RequestPasswordResetCommand;
import com.example.backend.identity.command.ResetPasswordCommand;
import com.example.backend.identity.command.UserCommandService;
import com.example.backend.identity.query.CurrentUserView;
import com.example.backend.identity.query.UserQueryService;
import com.example.backend.identity.security.AuthenticatedUser;
import com.example.backend.identity.security.SessionAuthenticator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Session authentication endpoints. The OpenAPI contract is documented on {@link AuthApi}. */
@RestController
@RequestMapping(path = ApiVersions.BASE + "/auth", version = ApiVersions.V1)
public class AuthController implements AuthApi {

    private final UserCommandService commands;
    private final UserQueryService queries;
    private final SessionAuthenticator sessions;

    public AuthController(UserCommandService commands, UserQueryService queries, SessionAuthenticator sessions) {
        this.commands = commands;
        this.queries = queries;
        this.sessions = sessions;
    }

    @Override
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(HttpServletRequest request) {
        // Reading the deferred token makes the repository write the XSRF-TOKEN cookie.
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
    }

    @Override
    @PostMapping("/register")
    public ResponseEntity<CurrentUserView> register(@RequestBody RegisterRequest body,
                                                    HttpServletRequest request, HttpServletResponse response) {
        commands.handle(new RegisterUserCommand(body.email(), body.displayName(), body.password()));
        AuthenticatedUser user = sessions.signIn(body.email(), body.password(), request, response);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri().resolve("me");
        return ResponseEntity.created(location).body(queries.findCurrent(user.id()));
    }

    @Override
    @PostMapping("/login")
    public CurrentUserView login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedUser user = sessions.signIn(body.email(), body.password(), request, response);
        return queries.findCurrent(user.id());
    }

    @Override
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.signOut(request, response);
    }

    @Override
    @GetMapping("/me")
    public CurrentUserView me(@AuthenticationPrincipal AuthenticatedUser user) {
        return queries.findCurrent(user.id());
    }

    @Override
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@RequestBody PasswordResetRequest body) {
        commands.handle(new RequestPasswordResetCommand(body.email()));
    }

    @Override
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestBody PasswordResetConfirmRequest body) {
        commands.handle(new ResetPasswordCommand(body.token(), body.newPassword()));
    }
}
