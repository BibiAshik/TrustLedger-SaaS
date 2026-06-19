package com.trustledgersaas.repository;

import com.trustledgersaas.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * CustomerRepository — Data access layer for the Customer entity.
 *
 * Provides methods for shop owners to manage their customers and for the
 * customer login flow to find matching records across multiple shops
 * (the multi-shop disambiguation logic from Section 6.6).
 *
 * Note: Uniqueness is scoped to (phone + shop) and (email + shop),
 * NOT globally unique across all shops.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Find all customers belonging to a specific shop — paginated list for shop owner */
    Page<Customer> findByShopId(Long shopId, Pageable pageable);

    /** Find a customer by phone and shop — ensures uniqueness within a shop */
    Optional<Customer> findByPhoneAndShopId(String phone, Long shopId);

    /** Find a customer by email and shop — ensures uniqueness within a shop */
    Optional<Customer> findByEmailAndShopId(String email, Long shopId);

    /**
     * Find ALL customers with a given phone number (across all shops).
     * Used for the multi-shop login disambiguation logic:
     * the same phone can exist at different shops, so this returns all matches.
     */
    List<Customer> findAllByPhone(String phone);

    /**
     * Find ALL customers with a given email (across all shops).
     * Used for the multi-shop login disambiguation logic.
     */
    List<Customer> findAllByEmail(String email);

    /** Count total customers for a shop — used for the 100-customer limit on Basic plan */
    long countByShopId(Long shopId);

    /** Find a customer by their linked User account ID */
    Optional<Customer> findByUserId(Long userId);

    /** Find a customer by their linked User */
    Optional<Customer> findByUser(com.trustledgersaas.entity.User user);

    /**
     * Search customers by name prefix within a specific shop.
     * Used for the live search bar on the customer list page.
     * Matches customer names that START WITH the typed letters.
     */
    Page<Customer> findByShopIdAndFullNameStartingWithIgnoreCase(Long shopId, String namePrefix, Pageable pageable);

    /** Find all customers for a shop as a simple list (no pagination) */
    List<Customer> findByShopId(Long shopId);
}
