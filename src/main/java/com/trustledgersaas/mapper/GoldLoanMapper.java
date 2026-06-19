package com.trustledgersaas.mapper;

import com.trustledgersaas.dto.response.GoldLoanResponseDTO;
import com.trustledgersaas.entity.GoldLoan;
import com.trustledgersaas.util.InterestCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * GoldLoanMapper — Converts between GoldLoan entity and GoldLoanResponseDTO.
 *
 * This mapper is special because it also computes LIVE interest calculations
 * when building the DTO. The interest fields on the DTO (totalInterestAccrued,
 * balanceDue, dailyInterestAmount) are NOT read from the database — they are
 * calculated fresh every time using the InterestCalculator utility.
 */
public class GoldLoanMapper {

    /**
     * Converts a GoldLoan entity to a GoldLoanResponseDTO with live interest calculations.
     *
     * Purpose: Build a complete loan DTO including freshly-computed interest.
     * Input: A GoldLoan entity + the total amount already paid on this loan.
     * Output: A GoldLoanResponseDTO with all fields populated, including live interest.
     */
    public static GoldLoanResponseDTO toDTO(GoldLoan loan, BigDecimal totalPaid) {

        // Calculate live interest using the InterestCalculator utility
        BigDecimal totalInterestAccrued = InterestCalculator.calculateTotalInterestAccrued(
                loan.getLoanAmount(), loan.getInterestRate(), loan.getLoanDate());

        BigDecimal balanceDue = InterestCalculator.calculateBalanceDue(
                loan.getLoanAmount(), loan.getInterestRate(), loan.getLoanDate(), totalPaid);

        BigDecimal dailyInterest = InterestCalculator.calculateDailyInterest(
                loan.getLoanAmount(), loan.getInterestRate());

        // Calculate days overdue (0 if not overdue)
        long daysOverdue = 0;
        if (loan.getDueDate() != null && LocalDate.now().isAfter(loan.getDueDate())) {
            daysOverdue = ChronoUnit.DAYS.between(loan.getDueDate(), LocalDate.now());
        }

        return GoldLoanResponseDTO.builder()
                .id(loan.getId())
                .loanId(loan.getLoanId())
                .loanAmount(loan.getLoanAmount())
                .interestRate(loan.getInterestRate())
                .loanDate(loan.getLoanDate())
                .dueDate(loan.getDueDate())
                .status(loan.getStatus())
                .goldItemType(loan.getGoldItemType())
                .goldItemDescription(loan.getGoldItemDescription())
                .goldWeight(loan.getGoldWeight())
                .goldPurity(loan.getGoldPurity())
                .estimatedValue(loan.getEstimatedValue())
                .customerId(loan.getCustomer() != null ? loan.getCustomer().getId() : null)
                .customerName(loan.getCustomer() != null ? loan.getCustomer().getFullName() : null)
                .customerPhone(loan.getCustomer() != null ? loan.getCustomer().getPhone() : null)
                .shopId(loan.getShop() != null ? loan.getShop().getId() : null)
                .shopName(loan.getShop() != null ? loan.getShop().getShopName() : null)
                .totalInterestAccrued(totalInterestAccrued)
                .totalPaid(totalPaid)
                .balanceDue(balanceDue)
                .dailyInterestAmount(dailyInterest)
                .seizureWarningShown(loan.isSeizureWarningShown())
                .closedAt(loan.getClosedAt())
                .createdAt(loan.getCreatedAt())
                .daysOverdue(daysOverdue)
                .build();
    }
}
