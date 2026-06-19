package com.trustledgersaas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SubscriptionPaymentResponseDTO — Carries subscription payment data
 * for the Super Admin's revenue log and the shop owner's subscription history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPaymentResponseDTO {

    private Long id;
    private Long shopId;
    private String shopName;
    private BigDecimal amount;
    private String planType;
    private LocalDateTime paymentDate;
    private String razorpayPaymentId;
    private LocalDateTime createdAt;
}
