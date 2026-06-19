package com.trustledgersaas.repository;

import com.trustledgersaas.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * PaymentRepository — Data access layer for the Payment entity.
 *
 * Provides methods for querying payments by loan, shop, and customer.
 * Also includes sum calculations for interest paid on a loan.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Find all payments for a specific loan — full payment history per loan */
    List<Payment> findByGoldLoanIdOrderByPaymentDateDesc(Long goldLoanId);

    /** Find all payments for a specific loan — paginated */
    Page<Payment> findByGoldLoanId(Long goldLoanId, Pageable pageable);

    /**
     * Find all payments across all loans for a specific shop — paginated.
     * Used for the shop owner's overall payment history view.
     */
    @Query("SELECT p FROM Payment p WHERE p.goldLoan.shop.id = :shopId ORDER BY p.paymentDate DESC")
    Page<Payment> findByShopId(@Param("shopId") Long shopId, Pageable pageable);

    /**
     * Find all payments for a specific customer (across all their loans).
     * Used for the customer's payment history page.
     */
    @Query("SELECT p FROM Payment p WHERE p.goldLoan.customer.id = :customerId ORDER BY p.paymentDate DESC")
    Page<Payment> findByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    /**
     * Sum of all payment amounts for a specific loan.
     * This is the "total paid so far" — subtracted from total interest accrued
     * to get the "balance due" in the interest calculation.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.goldLoan.id = :loanId")
    BigDecimal sumPaymentsByLoanId(@Param("loanId") Long loanId);

    /** Find the most recent payments for a shop — for the dashboard "recent payments" list */
    @Query("SELECT p FROM Payment p WHERE p.goldLoan.shop.id = :shopId ORDER BY p.paymentDate DESC")
    List<Payment> findRecentByShopId(@Param("shopId") Long shopId, Pageable pageable);

    /** Find the last receipt number — used to generate the next sequential receipt number */
    @Query("SELECT p.receiptNumber FROM Payment p ORDER BY p.id DESC LIMIT 1")
    Optional<String> findLastReceiptNumber();

    /** Count total payments for a loan */
    long countByGoldLoanId(Long goldLoanId);
}
