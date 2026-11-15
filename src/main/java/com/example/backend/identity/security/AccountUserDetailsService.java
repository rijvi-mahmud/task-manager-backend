package com.example.backend.identity.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.common.exception.DomainValidationException;
import com.example.backend.common.validation.Emails;
import com.example.backend.identity.domain.User;
import com.example.backend.identity.domain.UserRepository;

/**
 * Loads accounts for password sign-in. Pending (invited, unregistered)
 * accounts cannot sign in and are reported exactly like unknown emails.
 */
@Service
@Transactional(readOnly = true)
public class AccountUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AccountUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String normalized;
        try {
            normalized = Emails.normalize(email);
        } catch (DomainValidationException ex) {
            throw new UsernameNotFoundException("Unknown account");
        }
        return users.findByEmail(normalized)
                .filter(User::isRegistered)
                .map(user -> new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }
}
