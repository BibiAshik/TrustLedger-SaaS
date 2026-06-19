package com.trustledgersaas.repository;

import com.trustledgersaas.entity.SubscriptionPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SubscriptionPaymentRepository — Data access layer for the SubscriptionPayment entity.
 *
 * Provides methods for querying subscription payment history by shop,
 * and for the Super Admin's subscription revenue analytics.
 */
@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    /** Find all subscription payments for a specific shop — their payment history */
    List<SubscriptionPayment> findByShopIdOrderByPaymentDateDesc(Long shopId);

    /** Find all subscription payments — paginated, for Super Admin revenue log */
    Page<SubscriptionPayment> findAllByOrderByPaymentDateDesc(Pageable pageable);

    /**
     * Sum of subscription payments in a given month — for Super Admin analytics.
     * Used to calculate "this month's subscription revenue".
     */
    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM SubscriptionPayment sp " +
           "WHERE sp.paymentDate >= :startOfMonth AND sp.paymentDate < :startOfNextMonth")
    BigDecimal sumPaymentsInMonth(@Param("startOfMonth") LocalDateTime startOfMonth,
                                  @Param("startOfNextMonth") LocalDateTime startOfNextMonth);
}
