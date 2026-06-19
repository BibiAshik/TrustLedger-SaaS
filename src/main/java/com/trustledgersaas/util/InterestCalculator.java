package com.trustledgersaas.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * InterestCalculator — The single most important piece of business logic in Trust Ledger.
 *
 * This utility class calculates the live interest accrued on a gold loan.
 * Interest is NEVER stored as a static field in the database — it is always
 * computed fresh every time it needs to be displayed (on page load, before
 * a payment, etc.).
 *
 * THE FORMULA (explained step by step):
 *
 *   1. daysElapsed = today's date − loan's start date (loanDate)
 *      This tells us how many days the borrower has had the money.
 *
 *   2. monthlyInterestAmount = loanAmount × (interestRate / 100)
 *      For example, if loanAmount = ₹10,000 and interestRate = 2%,
 *      then monthlyInterestAmount = 10000 × 0.02 = ₹200 per month.
 *
 *   3. dailyInterestAmount = monthlyInterestAmount ÷ 30
 *      We divide the monthly interest by 30 to get the daily rate.
 *      For example, ₹200 ÷ 30 = ₹6.67 per day.
 *
 *   4. totalInterestAccrued = dailyInterestAmount × daysElapsed
 *      Multiply the daily rate by how many days have passed.
 *      For example, after 45 days: ₹6.67 × 45 = ₹300.
 *
 *   5. balanceDue = totalInterestAccrued − totalPaymentsMade
 *      Subtract whatever the customer has already paid.
 *      If they paid ₹100, then balance = ₹300 - ₹100 = ₹200.
 */
public class InterestCalculator {

    /**
     * Calculates the total interest accrued on a loan from its start date until today.
     *
     * Purpose: Compute how much interest a borrower owes right now.
     * Input:
     *   - loanAmount: The principal amount lent (e.g. ₹10,000)
     *   - interestRate: Monthly interest rate as a percentage (e.g. 2.0 means 2%)
     *   - loanDate: The date the loan was created / money was given
     * Output: The total interest accrued from loanDate to today (BigDecimal, 2 decimal places)
     */
    public static BigDecimal calculateTotalInterestAccrued(BigDecimal loanAmount,
                                                            BigDecimal interestRate,
                                                            LocalDate loanDate) {

        // Step 1: Calculate how many days have elapsed since the loan started
        long daysElapsed = ChronoUnit.DAYS.between(loanDate, LocalDate.now());

        // If the loan was created today or in the future, no interest has accrued yet
        if (daysElapsed <= 0) {
            return BigDecimal.ZERO;
        }

        // Step 2: Calculate the monthly interest amount
        // monthlyInterestAmount = loanAmount × (interestRate / 100)
        // Example: 10000 × (2 / 100) = 10000 × 0.02 = 200
        BigDecimal monthlyInterestAmount = loanAmount.multiply(interestRate)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        // Step 3: Calculate the daily interest amount
        // dailyInterestAmount = monthlyInterestAmount ÷ 30
        // Example: 200 ÷ 30 = 6.6667
        BigDecimal dailyInterestAmount = monthlyInterestAmount
                .divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);

        // Step 4: Calculate the total interest accrued
        // totalInterestAccrued = dailyInterestAmount × daysElapsed
        // Example: 6.6667 × 45 = 300.00
        BigDecimal totalInterestAccrued = dailyInterestAmount
                .multiply(BigDecimal.valueOf(daysElapsed))
                .setScale(2, RoundingMode.HALF_UP);

        return totalInterestAccrued;
    }

    /**
     * Calculates the balance due on a loan (interest accrued minus payments made).
     *
     * Purpose: Determine how much the customer still owes right now.
     * Input:
     *   - loanAmount: The principal amount lent
     *   - interestRate: Monthly interest rate as a percentage
     *   - loanDate: The date the loan was created
     *   - totalPaymentsMade: Sum of all payments already made on this loan
     * Output: The balance due (can be negative if overpaid — rare but possible)
     */
    public static BigDecimal calculateBalanceDue(BigDecimal loanAmount,
                                                  BigDecimal interestRate,
                                                  LocalDate loanDate,
                                                  BigDecimal totalPaymentsMade) {

        // Step 1-4: Calculate total interest accrued
        BigDecimal totalInterestAccrued = calculateTotalInterestAccrued(loanAmount, interestRate, loanDate);

        // Step 5: Subtract total payments made to get the balance due
        // balanceDue = totalInterestAccrued − totalPaymentsMade
        BigDecimal balanceDue = totalInterestAccrued.subtract(totalPaymentsMade)
                .setScale(2, RoundingMode.HALF_UP);

        return balanceDue;
    }

    /**
     * Calculates the daily interest amount for display purposes.
     *
     * Purpose: Show the customer/shop owner how much interest accrues per day.
     * Input:
     *   - loanAmount: The principal amount lent
     *   - interestRate: Monthly interest rate as a percentage
     * Output: The daily interest amount (e.g. ₹6.67 per day)
     */
    public static BigDecimal calculateDailyInterest(BigDecimal loanAmount, BigDecimal interestRate) {
        BigDecimal monthlyInterest = loanAmount.multiply(interestRate)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        return monthlyInterest.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
    }
}
