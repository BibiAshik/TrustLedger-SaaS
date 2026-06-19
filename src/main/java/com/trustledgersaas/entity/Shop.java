package com.trustledgersaas.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Shop — Represents a gold loan shop (tenant) on the Trust Ledger platform.
 *
 * Each Shop is an independent business that uses Trust Ledger to manage their
 * gold loan operations. "Multi-tenant" means many shops share the same database
 * and application, but each shop's data is completely isolated from others.
 *
 * Relationships:
 * - One Shop belongs to exactly one User (the shop owner) — @OneToOne with User
 * - One Shop has many Customers — @OneToMany with Customer
 * - One Shop has many GoldLoans (indirectly through customers, but also directly
 *   for quick querying) — @OneToMany with GoldLoan
 * - One Shop has many SubscriptionPayments — @OneToMany with SubscriptionPayment
 *
 * Lifecycle: PENDING → APPROVED (by Super Admin) → ACTIVE (after subscription payment)
 * Can also be SUSPENDED by Super Admin at any time.
 */
@Entity
@Table(name = "shops")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================== SHOP BASIC INFO ====================

    /** The public-facing name of the gold loan shop */
    @Column(nullable = false)
    private String shopName;

    /** Full legal name of the shop owner */
    @Column(nullable = false)
    private String ownerFullName;

    /** File path to the owner's uploaded photo (used for ID verification by Super Admin) */
    private String ownerPhotoPath;

    /** Shop's contact phone number */
    @Column(nullable = false)
    private String phone;

    /** Shop owner's email (same as the linked User's email) */
    @Column(nullable = false)
    private String email;

    /** Physical address of the shop */
    @Column(nullable = false)
    private String address;

    /** City where the shop is located */
    @Column(nullable = false)
    private String city;

    /** Postal/PIN code */
    @Column(nullable = false)
    private String pincode;

    // ==================== KYC DOCUMENTS ====================

    /** File path to Aadhaar card front image */
    private String aadhaarFrontPath;

    /** File path to Aadhaar card back image */
    private String aadhaarBackPath;

    /** File path to PAN card image/PDF */
    private String panPath;

    /** Owner's PAN number — required by Razorpay to create a Linked Account */
    private String panNumber;

    /**
     * Business type — required by Razorpay Route for Linked Account creation.
     * Accepted values include "individual", "proprietorship", "partnership",
     * "private_limited", "public_limited", "llp", "trust", and "society".
     */
    private String businessType;

    // ==================== BANK DETAILS ====================
    // These are used for Razorpay Linked Account creation (Route),
    // so customer payments go directly to the shop's bank account.

    /** Bank account number for receiving customer payments */
    @Column(nullable = false)
    private String bankAccountNumber;

    /** IFSC code of the bank branch */
    @Column(nullable = false)
    private String ifscCode;

    /** Name as it appears on the bank account */
    @Column(nullable = false)
    private String accountHolderName;

    /** UPI ID for receiving payments (e.g. shopname@upi) */
    private String upiId;

    // ==================== OPTIONAL DOCUMENTS ====================

    /** GST registration number — optional, many small shops won't have this */
    private String gstNumber;

    /** File path to shop license document — optional */
    private String shopLicensePath;

    // ==================== STATUS & PLAN ====================

    /**
     * Current status of the shop account.
     * PENDING  — just registered, awaiting Super Admin review
     * APPROVED — Super Admin approved, awaiting first subscription payment
     * ACTIVE   — fully active, subscription is current
     * SUSPENDED — blocked by Super Admin, cannot log in
     */
    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    /**
     * Current subscription plan. Determines which features are available.
     * BASIC — core features, no customer portal, 100 customer limit
     * PRO   — all features including customer portal, online payments, unlimited customers
     */
    @Builder.Default
    private String plan = "BASIC";

    /** Date when the current subscription period started */
    private LocalDate subscriptionStartDate;

    /** Date when the current subscription period expires */
    private LocalDate subscriptionExpiryDate;

    /**
     * Current subscription payment status.
     * ACTIVE  — subscription is paid and current
     * EXPIRED — subscription period has ended
     * NONE    — no subscription purchased yet (new shop)
     */
    @Builder.Default
    private String subscriptionStatus = "NONE";

    /** Reason provided by Super Admin when rejecting a shop registration */
    @Column(length = 1000)
    private String rejectionReason;

    /**
     * Set to true when Super Admin opens the shop detail page for review.
     * Used on the shop owner's application status tracker.
     */
    @Builder.Default
    private boolean applicationViewed = false;

    // ==================== RAZORPAY ====================

    /**
     * Razorpay Linked Account ID — created automatically when a shop activates
     * on the PRO plan. Used for Route transfers so customer payments go directly
     * to this shop's bank account, never through the Super Admin's account.
     */
    private String razorpayLinkedAccountId;

    // ==================== RELATIONSHIPS ====================

    /**
     * The User account that this shop owner logs in with.
     * One Shop has exactly one User (OneToOne relationship).
     */
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * All customers belonging to this shop.
     * One Shop has many Customers (OneToMany relationship).
     * cascade = ALL means when we save/update the shop, its customers are also saved.
     * mappedBy = "shop" means the Customer entity owns the foreign key.
     */
    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Customer> customers = new ArrayList<>();

    /**
     * All gold loans created by this shop.
     * One Shop has many GoldLoans (directly linked for easy querying).
     */
    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GoldLoan> goldLoans = new ArrayList<>();

    /**
     * All subscription payments made by this shop.
     */
    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SubscriptionPayment> subscriptionPayments = new ArrayList<>();

    // ==================== TIMESTAMPS ====================

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
