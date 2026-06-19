package com.trustledgersaas.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SubscriptionPayment — Records each subscription payment made by a shop owner.
 *
 * When a shop owner pays for their subscription (Basic ₹299/mo or Pro ₹699/mo),
 * a record is created here. This is separate from the Payment entity, which
 * tracks customer loan payments.
 *
 * These payments go to the Super Admin's own Razorpay account directly —
 * this is "Relationship A" in the payment architecture (shop pays Super Admin).
 *
 * Relationships:
 * - Many SubscriptionPayments belong to one Shop — @ManyToOne with Shop
 */
@Entity
@Table(name = "subscription_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The shop that made this subscription payment.
     * Many payments can be made over time (monthly renewals).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    /** Amount paid (₹299 for Basic, ₹699 for Pro) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Which plan this payment is for: BASIC or PRO */
    @Column(nullable = false)
    private String planType;

    /** When this payment was made */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime paymentDate = LocalDateTime.now();

    /** Razorpay payment ID from the subscription checkout */
    private String razorpayPaymentId;

    /** Razorpay order ID from the subscription checkout */
    private String razorpayOrderId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
