package com.trustledgersaas.repository;

import com.trustledgersaas.entity.GoldLoan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * GoldLoanRepository — Data access layer for the GoldLoan entity.
 *
 * Provides methods for querying loans by customer, shop, status,
 * and due date. Also includes analytics queries for dashboard stats.
 */
@Repository
public interface GoldLoanRepository extends JpaRepository<GoldLoan, Long> {

    /** Find all loans for a specific customer — paginated */
    Page<GoldLoan> findByCustomerId(Long customerId, Pageable pageable);

    /** Find all loans for a specific customer — simple list */
    List<GoldLoan> findByCustomerId(Long customerId);

    /** Find all loans for a specific shop — paginated list for shop owner dashboard */
    Page<GoldLoan> findByShopId(Long shopId, Pageable pageable);

    /** Find loans by shop and status — e.g. all OVERDUE loans for a shop */
    Page<GoldLoan> findByShopIdAndStatus(Long shopId, String status, Pageable pageable);

    /** Find loans by status across all shops — used by the daily scheduler */
    List<GoldLoan> findByStatus(String status);

    /**
     * Find all ACTIVE loans whose due date has already passed.
     * Used by the daily scheduled job to flip these from ACTIVE to OVERDUE.
     */
    List<GoldLoan> findByStatusAndDueDateBefore(String status, LocalDate date);

    /** Find a loan by its human-readable loan ID (e.g. "LN-2026-00341") */
    Optional<GoldLoan> findByLoanId(String loanId);

    /** Count active loans for a specific shop — for dashboard stats */
    long countByShopIdAndStatus(Long shopId, String status);

    /** Count total loans for a customer */
    long countByCustomerId(Long customerId);

    long countByCustomerIdAndStatus(Long customerId, String status);

    /** Count total loans across all shops — for Super Admin analytics */
    long countByStatus(String status);

    /**
     * Sum of all loan amounts across all shops — for Super Admin analytics.
     * Returns the total loan volume on the platform.
     */
    @Query("SELECT COALESCE(SUM(g.loanAmount), 0) FROM GoldLoan g")
    BigDecimal sumAllLoanAmounts();

    /**
     * Sum of loan amounts for a specific shop — for shop dashboard stats.
     */
    @Query("SELECT COALESCE(SUM(g.loanAmount), 0) FROM GoldLoan g WHERE g.shop.id = :shopId AND g.status = :status")
    BigDecimal sumLoanAmountsByShopIdAndStatus(@Param("shopId") Long shopId, @Param("status") String status);

    @Query("SELECT COALESCE(SUM(g.loanAmount), 0) FROM GoldLoan g WHERE g.shop.id = :shopId")
    BigDecimal sumLoanAmountsByShopId(@Param("shopId") Long shopId);

    /**
     * Find loans for a specific shop that are due within a date range.
     * Used for "loans due this week" on the shop dashboard.
     */
    List<GoldLoan> findByShopIdAndStatusAndDueDateBetween(Long shopId, String status, LocalDate startDate, LocalDate endDate);
}
