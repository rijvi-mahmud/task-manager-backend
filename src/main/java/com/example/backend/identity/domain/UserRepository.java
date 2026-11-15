package com.example.backend.identity.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** Write-side repository for users. */
public interface UserRepository extends JpaRepository<User, Long> {

    /** @param email already normalized (lower-case) */
    Optional<User> findByEmail(String email);

    /** Serialize claims of an existing invitation; new accounts still use the unique email constraint. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
}
