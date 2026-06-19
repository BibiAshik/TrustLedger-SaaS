package com.trustledgersaas.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * LoanCreateRequestDTO — Carries loan creation form data from the Shop Owner dashboard
 * to LoanService.
 *
 * The Shop Owner selects a customer, enters loan and gold item details.
 * Loan date validation (no past dates) is enforced both here and in the service layer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanCreateRequestDTO {

    /** ID of the customer this loan is being created for */
    @NotNull(message = "Customer must be selected")
    private Long customerId;

    /** Principal loan amount in INR */
    @NotNull(message = "Loan amount is required")
    @DecimalMin(value = "100.00", message = "Loan amount must be at least ₹100")
    private BigDecimal loanAmount;

    /** Monthly interest rate as a percentage (e.g. 2.0 means 2% per month) */
    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.1", message = "Interest rate must be at least 0.1%")
    @DecimalMax(value = "10.0", message = "Interest rate cannot exceed 10%")
    private BigDecimal interestRate;

    /** Loan start date (defaults to today, MUST NOT be in the past) */
    @NotBlank(message = "Loan date is required")
    private String loanDate;

    /** Due date (auto-suggested as loanDate + 3 months, but editable) */
    @NotBlank(message = "Due date is required")
    private String dueDate;

    /**
     * Type of gold item pledged.
     * Preset options: Gold Chain, Gold Ring, Gold Bangle, Gold Necklace,
     * Gold Bracelet, Gold Earrings, Gold Coin, Others
     */
    @NotBlank(message = "Gold item type is required")
    private String goldItemType;

    /** Custom description — only used when goldItemType is "Others" */
    private String goldItemDescription;

    /** Weight of the gold item in grams */
    @NotNull(message = "Gold weight is required")
    @DecimalMin(value = "0.1", message = "Gold weight must be at least 0.1 grams")
    private BigDecimal goldWeight;

    /** Purity of the gold (e.g. "22K", "18K", "24K") */
    @NotBlank(message = "Gold purity is required")
    private String goldPurity;

    /** Estimated market value of the gold item in INR */
    private BigDecimal estimatedValue;
}
