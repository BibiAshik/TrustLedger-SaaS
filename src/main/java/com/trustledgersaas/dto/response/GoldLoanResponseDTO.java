package com.trustledgersaas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GoldLoanResponseDTO — Carries loan data from the backend to the frontend.
 *
 * Includes LIVE-CALCULATED interest fields (totalInterestAccrued, balanceDue)
 * that are computed fresh every time — never stored in the database.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoldLoanResponseDTO {

    private Long id;
    private String loanId;
    private BigDecimal loanAmount;
    private BigDecimal interestRate;
    private LocalDate loanDate;
    private LocalDate dueDate;
    private String status;

    // Gold item details
    private String goldItemType;
    private String goldItemDescription;
    private BigDecimal goldWeight;
    private String goldPurity;
    private BigDecimal estimatedValue;

    // Customer info (for display in loan lists)
    private Long customerId;
    private String customerName;
    private String customerPhone;

    // Shop info
    private Long shopId;
    private String shopName;

    // ==================== LIVE-CALCULATED FIELDS ====================
    // These are NEVER stored in the database. They are computed fresh
    // every time this DTO is built (see GoldLoanMapper.toDTO()).

    /** Total interest accrued from loan date to today — computed live */
    private BigDecimal totalInterestAccrued;

    /** Sum of all payments made on this loan so far */
    private BigDecimal totalPaid;

    /** Interest remaining = totalInterestAccrued - totalPaid */
    private BigDecimal balanceDue;

    /** How much interest accrues each day on this loan */
    private BigDecimal dailyInterestAmount;

    // Flags
    private boolean seizureWarningShown;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;

    /** Number of days overdue (0 if not overdue) */
    private long daysOverdue;
}
