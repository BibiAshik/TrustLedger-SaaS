package com.trustledgersaas.repository;

import com.trustledgersaas.entity.Shop;
import com.trustledgersaas.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * ShopRepository — Data access layer for the Shop entity.
 *
 * Provides methods for the Super Admin to manage shop registrations
 * (approval queue, active shops, suspended shops) and for querying
 * shop details and analytics.
 */
@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    /** Find shops by status — used for the Super Admin approval queue (status=PENDING) */
    Page<Shop> findByStatus(String status, Pageable pageable);

    /** Find all shops by status as a list — used when pageable is not needed */
    List<Shop> findByStatus(String status);

    /** Find a shop by its linked User account — used to get the current shop owner's shop after login */
    Optional<Shop> findByUser(User user);

    /** Find a shop by the user ID — shortcut to avoid loading the full User entity */
    Optional<Shop> findByUserId(Long userId);

    /** Find a shop by owner email — used for public application status lookup */
    Optional<Shop> findByEmail(String email);

    /** Count shops by status — used for Super Admin dashboard analytics */
    long countByStatus(String status);

    /** Count shops by subscription status — for analytics (how many active subscriptions) */
    long countBySubscriptionStatus(String subscriptionStatus);

    /** Count shops by plan type — for analytics */
    long countByPlan(String plan);

    /** Find all shops with a specific subscription status — for batch operations like expiry checks */
    List<Shop> findBySubscriptionStatus(String subscriptionStatus);

    /** Find all shops — paginated list for Super Admin shop management */
    Page<Shop> findAll(Pageable pageable);

    /** Check if a shop name already exists — used during registration validation */
    boolean existsByShopName(String shopName);
}
