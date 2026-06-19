package com.trustledgersaas.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PaymentRecordRequestDTO — Carries cash payment form data from the Shop Owner
 * to PaymentService when manually recording a cash payment.
 *
 * Online payments are NOT recorded through this DTO — they come through
 * the Razorpay verification flow via PaymentVerifyRequestDTO instead.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRecordRequestDTO {

    /** The loan this payment is being applied to */
    @NotNull(message = "Loan ID is required")
    private Long loanId;

    /** The cash amount being paid */
    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "1.00", message = "Payment amount must be at least ₹1")
    private BigDecimal amount;

    /** Date the cash payment was received (ISO format: yyyy-MM-dd) */
    @NotBlank(message = "Payment date is required")
    private String paymentDate;

    /** Optional note from the shop owner about this payment */
    @Size(max = 500, message = "Note cannot exceed 500 characters")
    private String note;
}
