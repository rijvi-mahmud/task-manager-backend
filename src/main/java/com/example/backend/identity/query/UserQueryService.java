package com.example.backend.identity.query;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.identity.domain.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserQueryService {
    private static final Logger log = LoggerFactory.getLogger(UserQueryService.class);

    private final UserRepository users;

    public UserQueryService(UserRepository users) {
        this.users = users;
    }

    public CurrentUserView findCurrent(Long userId) {
        log.debug("Current user read requested userId={}", userId);
        return users.findById(userId)
                .map(user -> new CurrentUserView(user.getId(), user.getEmail(), user.getDisplayName()))
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
