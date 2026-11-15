package com.example.backend.identity.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Clock;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.backend.common.exception.ConflictException;
import com.example.backend.identity.domain.PasswordResetTokenRepository;
import com.example.backend.identity.domain.User;
import com.example.backend.identity.domain.UserRepository;
import com.example.backend.identity.security.AuthRateLimiter;

class UserRegistrationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UserCommandService commands = new UserCommandService(users, mock(PasswordResetTokenRepository.class),
            passwords, mock(PasswordResetLinkSender.class), events, Clock.systemUTC(), URI.create("http://localhost:5173"),
            mock(AuthRateLimiter.class));

    @BeforeEach void encodePassword() {
        when(passwords.encode("new-password")).thenReturn("encoded-new-password");
    }

    @Test void claimsAnInvitationThroughTheLockedLookup() {
        User invited = User.pending("ada@example.com");
        ReflectionTestUtils.setField(invited, "id", 42L);
        when(users.findByEmailForUpdate("ada@example.com")).thenReturn(Optional.of(invited));

        assertThat(commands.handle(new RegisterUserCommand(" ADA@example.com ", "Ada", "new-password"))).isEqualTo(42L);
        assertThat(invited.getPasswordHash()).isEqualTo("encoded-new-password");
        assertThat(invited.getDisplayName()).isEqualTo("Ada");
        verify(users, never()).findByEmail(anyString());
        verify(users, never()).save(any(User.class));
    }

    @Test void rejectsAnInvitationThatAnotherTransactionAlreadyClaimed() {
        User claimed = User.registered("ada@example.com", "First registrant", "first-password-hash");
        when(users.findByEmailForUpdate("ada@example.com")).thenReturn(Optional.of(claimed));

        assertThatThrownBy(() -> commands.handle(new RegisterUserCommand("ada@example.com", "Second registrant", "new-password")))
                .isInstanceOf(ConflictException.class);
        assertThat(claimed.getPasswordHash()).isEqualTo("first-password-hash");
        assertThat(claimed.getDisplayName()).isEqualTo("First registrant");
        verifyNoInteractions(events);
    }
}
