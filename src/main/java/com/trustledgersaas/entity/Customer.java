package com.trustledgersaas.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Customer — Represents a borrower (end customer) at a specific gold loan shop.
 *
 * Customers NEVER self-register. Their accounts are created by the Shop Owner
 * when the customer pledges gold and a loan is created for them.
 *
 * IMPORTANT MULTI-TENANCY RULE:
 * The same phone number CAN exist as separate Customer records under different shops.
 * For example, the same real person could be a customer at Shop A and Shop B.
 * Uniqueness is scoped to (phone + shop) and (email + shop), NOT globally unique.
 * This is enforced by the @UniqueConstraint annotations on the table.
 *
 * Relationships:
 * - One Customer belongs to exactly one Shop — @ManyToOne with Shop
 * - One Customer has exactly one User (login account) — @OneToOne with User
 * - One Customer has many GoldLoans — @OneToMany with GoldLoan
 */
@Entity
@Table(name = "customers",
       uniqueConstraints = {
           // Same phone number can exist at different shops, but NOT at the same shop
           @UniqueConstraint(columnNames = {"phone", "shop_id"}),
           // Same email can exist at different shops, but NOT at the same shop
           @UniqueConstraint(columnNames = {"email", "shop_id"})
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Customer's full legal name */
    @Column(nullable = false)
    private String fullName;

    /**
     * Customer's phone number — COMPULSORY.
     * Used as one of the login identifiers (customer can log in with phone OR email).
     * Unique per shop, not globally unique.
     */
    @Column(nullable = false)
    private String phone;

    /**
     * Customer's email address — optional.
     * Used for email notifications and as an alternative login identifier when provided.
     * Unique per shop when present, not globally unique.
     */
    private String email;

    /** Customer's date of birth */
    private LocalDate dateOfBirth;

    /** Customer's gender (Male / Female / Other) */
    private String gender;

    /** Customer's residential address */
    @Column(length = 1000)
    private String address;

    /**
     * Customer's Aadhaar number — stored in full in the database for internal
     * verification purposes, but NEVER displayed in full on any frontend page.
     * Always masked as "XXXX XXXX 1234" (only last 4 digits visible) when rendered.
     */
    @Column(nullable = false)
    private String aadhaarNumber;

    /** File path to the uploaded Aadhaar front image */
    private String aadhaarFrontPath;

    /** File path to the uploaded customer photo */
    private String customerPhotoPath;

    /** PAN card number — MANDATORY for all customers */
    @Column(nullable = false)
    private String panNumber;

    // ==================== RELATIONSHIPS ====================

    /**
     * The shop this customer belongs to.
     * A customer belongs to exactly one shop (ManyToOne relationship).
     * The shop_id column is the foreign key.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    /**
     * The User account for this customer's login.
     * One Customer has exactly one User (OneToOne relationship).
     * Created automatically when the shop owner adds a new customer.
     */
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * All gold loans belonging to this customer.
     * One Customer can have many GoldLoans (one loan per gold item pledged).
     */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GoldLoan> goldLoans = new ArrayList<>();

    // ==================== TIMESTAMPS ====================

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
