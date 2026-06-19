package com.trustledgersaas.mapper;

import com.trustledgersaas.dto.response.PaymentResponseDTO;
import com.trustledgersaas.entity.Payment;

/**
 * PaymentMapper — Converts between Payment entity and PaymentResponseDTO.
 *
 * Includes loan and customer context fields for display in payment history views.
 */
public class PaymentMapper {

    /**
     * Converts a Payment entity to a PaymentResponseDTO for frontend display.
     *
     * Purpose: Build a DTO with payment details + loan/customer context.
     * Input: A Payment entity loaded from the database.
     * Output: A PaymentResponseDTO with all displayable fields.
     */
    public static PaymentResponseDTO toDTO(Payment payment) {
        return PaymentResponseDTO.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .paymentMode(payment.getPaymentMode())
                .receiptNumber(payment.getReceiptNumber())
                .note(payment.getNote())
                .createdAt(payment.getCreatedAt())
                .loanId(payment.getGoldLoan() != null ? payment.getGoldLoan().getId() : null)
                .loanDisplayId(payment.getGoldLoan() != null ? payment.getGoldLoan().getLoanId() : null)
                .customerName(payment.getGoldLoan() != null && payment.getGoldLoan().getCustomer() != null
                        ? payment.getGoldLoan().getCustomer().getFullName() : null)
                .customerPhone(payment.getGoldLoan() != null && payment.getGoldLoan().getCustomer() != null
                        ? payment.getGoldLoan().getCustomer().getPhone() : null)
                .razorpayPaymentId(payment.getRazorpayPaymentId())
                .razorpayOrderId(payment.getRazorpayOrderId())
                .build();
    }
}
