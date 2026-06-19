package com.trustledgersaas.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PaymentVerifyRequestDTO — Carries Razorpay payment verification data
 * from the frontend to the backend after a customer completes online payment.
 *
 * After the Razorpay Checkout popup closes, Razorpay returns these three values
 * to the frontend. The frontend sends them to our backend for server-side
 * verification (HMAC-SHA256 signature check) before recording the payment.
 *
 * SECURITY: Never trust the frontend blindly — always verify the signature
 * server-side to confirm the payment wasn't spoofed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentVerifyRequestDTO {

    /** Razorpay Order ID — created by our backend before the checkout popup opened */
    @NotBlank(message = "Order ID is required")
    private String razorpayOrderId;

    /** Razorpay Payment ID — the unique identifier for this specific payment */
    @NotBlank(message = "Payment ID is required")
    private String razorpayPaymentId;

    /** Razorpay Signature — HMAC-SHA256 hash for server-side verification */
    @NotBlank(message = "Signature is required")
    private String razorpaySignature;

    /** The loan ID this payment is for — so we know which loan to credit */
    private Long loanId;

    /** The payment amount in INR — used to record the payment in our database */
    private java.math.BigDecimal amount;

    /**
     * The subscription plan type (BASIC or PRO).
     * Only used for subscription payments — null for loan interest payments.
     */
    private String planType;
}
