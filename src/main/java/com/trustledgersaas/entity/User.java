package com.trustledgersaas.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * User — The core authentication entity for ALL roles in Trust Ledger.
 *
 * Every person who logs into the system (Super Admin, Shop Owner, or Customer)
 * has exactly ONE User record. This entity holds login credentials, role info,
 * and account security fields (lockout, password reset).
 *
 * Relationships:
 * - A User with role ROLE_SHOP_OWNER is linked to exactly one Shop (via Shop.user)
 * - A User with role ROLE_CUSTOMER is linked to exactly one Customer (via Customer.user)
 * - A User with role ROLE_SUPER_ADMIN has no linked entity — there's only one, seeded at startup
 *
 * The "role" field stores values like "ROLE_SUPER_ADMIN", "ROLE_SHOP_OWNER", "ROLE_CUSTOMER"
 * — always prefixed with "ROLE_" as required by Spring Security's authority matching.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user's email address — used as the primary login identifier for Shop Owners and Super Admin */
    @Column(nullable = false)
    private String email;

    /** BCrypt-encoded password — never stored in plain text */
    @Column(nullable = false)
    private String password;

    /** The user's full name — displayed in the UI */
    @Column(nullable = false)
    private String fullName;

    /** Phone number — used as an alternative login identifier for Customers */
    private String phone;

    /**
     * The user's role in the system. Stored as a string in "ROLE_XXX" format.
     * Possible values: ROLE_SUPER_ADMIN, ROLE_SHOP_OWNER, ROLE_CUSTOMER
     * This is set automatically by the backend during account creation — never
     * accepted from a public form.
     */
    @Column(nullable = false)
    private String role;

    /**
     * Indicates if this is the user's first login (applies to Customers).
     * When true, the system forces a mandatory password change before the
     * customer can access any other page.
     */
    @Builder.Default
    private boolean isFirstLogin = false;

    /**
     * Tracks consecutive failed login attempts for account lockout.
     * After 5 consecutive failures, the account is locked for 12 hours.
     * Reset to 0 on successful login or after the lockout period expires.
     */
    @Builder.Default
    private int failedLoginAttempts = 0;

    /**
     * Timestamp until which the account is locked due to too many failed logins.
     * Null means the account is not locked.
     * If this timestamp is in the future, login attempts are blocked immediately
     * without even checking the password.
     */
    private LocalDateTime lockedUntil;

    /**
     * Token used for the "forgot password" email reset flow.
     * A random UUID is generated and emailed to the user as a reset link.
     * This field stores that token so the backend can verify it when clicked.
     */
    private String passwordResetToken;

    /** Expiry timestamp for the password reset token (e.g., 1 hour from creation) */
    private LocalDateTime passwordResetExpiry;

    /** When this user account was created */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** When this user account was last updated */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Automatically sets the updatedAt timestamp before every database update.
     * This is a JPA lifecycle callback — Hibernate calls this method automatically.
     */
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
