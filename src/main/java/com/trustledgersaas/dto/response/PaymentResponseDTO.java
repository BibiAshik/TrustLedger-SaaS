package com.trustledgersaas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PaymentResponseDTO — Carries payment data from the backend to the frontend.
 *
 * Used in: payment history views (both shop owner and customer sides),
 * and as part of the loan detail page's payment list.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDTO {

    private Long id;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentMode;
    private String receiptNumber;
    private String note;
    private LocalDateTime createdAt;

    // Context fields — which loan and customer this payment belongs to
    private Long loanId;
    private String loanDisplayId;    // Human-readable loan ID (e.g. "LN-2026-00341")
    private String customerName;
    private String customerPhone;

    // Razorpay details (null for cash payments)
    private String razorpayPaymentId;
    private String razorpayOrderId;

    /** Running balance remaining after this payment */
    private BigDecimal balanceAfterPayment;
}
