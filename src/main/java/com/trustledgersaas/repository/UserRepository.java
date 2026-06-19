package com.trustledgersaas.repository;

import com.trustledgersaas.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * UserRepository — Data access layer for the User entity.
 *
 * Provides methods to find users by email, phone, or both.
 * Used heavily by the authentication flow (login, registration,
 * password reset) and by the customer multi-shop disambiguation logic.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Find a user by their email address — used for Shop Owner and Super Admin login */
    Optional<User> findByEmail(String email);

    /** Find a user by their phone number — used for Customer login */
    Optional<User> findByPhone(String phone);

    /** Check if an email already exists in the system — used during registration to prevent duplicates */
    boolean existsByEmail(String email);

    /** Check if a phone number already exists — used during customer creation validation */
    boolean existsByPhone(String phone);

    /** Find all users matching a given email — used for customer multi-shop login disambiguation */
    List<User> findAllByEmail(String email);

    /** Find all users matching a given phone — used for customer multi-shop login disambiguation */
    List<User> findAllByPhone(String phone);

    /** Find a user by their password reset token — used in the forgot-password flow */
    Optional<User> findByPasswordResetToken(String token);

    /** Find a user by their role — e.g. to find the Super Admin account */
    Optional<User> findByRole(String role);
}
