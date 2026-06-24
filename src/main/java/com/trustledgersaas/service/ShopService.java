package com.trustledgersaas.service;

import com.trustledgersaas.dto.response.ShopResponseDTO;
import com.trustledgersaas.entity.Shop;
import com.trustledgersaas.entity.SubscriptionPayment;
import com.trustledgersaas.exception.InvalidRequestException;
import com.trustledgersaas.exception.ResourceNotFoundException;
import com.trustledgersaas.mapper.ShopMapper;
import com.trustledgersaas.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ShopService — Handles all shop management business logic.
 *
 * This includes:
 * - Super Admin: approve/reject shops, suspend shops, manage subscriptions
 * - Shop Owner: view/edit their own shop profile, view subscription status
 * - Platform analytics for the Super Admin dashboard
 */
@Service
@Slf4j
public class ShopService {

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private GoldLoanRepository goldLoanRepository;

    @Autowired
    private SubscriptionPaymentRepository subscriptionPaymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RazorpayService razorpayService;

    @Autowired
    private EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    // ==================== SUPER ADMIN — SHOP APPROVAL ====================

    /**
     * Gets all shops with a specific status (paginated).
     *
     * Purpose: Super Admin views the approval queue (PENDING shops) or all shops.
     * Input: Status filter (e.g. "PENDING") and Pageable for pagination.
     * Output: A Page of ShopResponseDTOs.
     */
    public Page<ShopResponseDTO> getShopsByStatus(String status, Pageable pageable) {
        Page<Shop> shops = shopRepository.findByStatus(status, pageable);
        return shops.map(shop -> {
            long customerCount = customerRepository.countByShopId(shop.getId());
            long activeLoanCount = goldLoanRepository.countByShopIdAndStatus(shop.getId(), "ACTIVE");
            long overdueLoanCount = goldLoanRepository.countByShopIdAndStatus(shop.getId(), "OVERDUE");
            return ShopMapper.toDTOWithCounts(shop, customerCount, activeLoanCount, overdueLoanCount);
        });
    }

    /**
     * Gets all shops (paginated) — for the Super Admin's full shop list.
     */
    public Page<ShopResponseDTO> getAllShops(Pageable pageable) {
        Page<Shop> shops = shopRepository.findAll(pageable);
        return shops.map(shop -> {
            long customerCount = customerRepository.countByShopId(shop.getId());
            long activeLoanCount = goldLoanRepository.countByShopIdAndStatus(shop.getId(), "ACTIVE");
            long overdueLoanCount = goldLoanRepository.countByShopIdAndStatus(shop.getId(), "OVERDUE");
            return ShopMapper.toDTOWithCounts(shop, customerCount, activeLoanCount, overdueLoanCount);
        });
    }

    /**
     * Gets detailed information about a specific shop.
     *
     * Purpose: Super Admin clicks into a shop to see full details.
     * Input: The shop's database ID.
     * Output: A ShopResponseDTO with all shop info and counts.
     */
    public ShopResponseDTO getShopById(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with ID: " + shopId));

        if ("PENDING".equals(shop.getStatus()) && !shop.isApplicationViewed()) {
            shop.setApplicationViewed(true);
            shopRepository.save(shop);
            log.info("Application marked as viewed for shop: {} (ID: {})", shop.getShopName(), shopId);
        }

        long customerCount = customerRepository.countByShopId(shopId);
        long activeLoanCount = goldLoanRepository.countByShopIdAndStatus(shopId, "ACTIVE");
        long overdueLoanCount = goldLoanRepository.countByShopIdAndStatus(shopId, "OVERDUE");

        return ShopMapper.toDTOWithCounts(shop, customerCount, activeLoanCount, overdueLoanCount);
    }

    /**
     * Gets a shop by the logged-in user's ID.
     *
     * Purpose: Shop Owner accesses their own shop data after login.
     * Input: The User ID from the JWT token.
     * Output: A ShopResponseDTO with the shop owner's shop info.
     */
    public ShopResponseDTO getShopByUserId(Long userId) {
        Shop shop = shopRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found for this user"));

        long customerCount = customerRepository.countByShopId(shop.getId());
        long activeLoanCount = goldLoanRepository.countByShopIdAndStatus(shop.getId(), "ACTIVE");
        long overdueLoanCount = goldLoanRepository.countByShopIdAndStatus(shop.getId(), "OVERDUE");

        return ShopMapper.toDTOWithCounts(shop, customerCount, activeLoanCount, overdueLoanCount);
    }

    /**
     * Approves a pending shop registration.
     *
     * Purpose: Super Admin approves a shop after reviewing their documents.
     * Input: The shop ID to approve.
     * Output: Map with success message. Shop status changes to APPROVED.
     */
    @Transactional
    public Map<String, String> approveShop(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with ID: " + shopId));

        if (!"PENDING".equals(shop.getStatus())) {
            throw new InvalidRequestException("This shop is not in PENDING status. Current status: " + shop.getStatus());
        }

        shop.setStatus("APPROVED");
        shop.setSubscriptionExpiryDate(LocalDate.now()); // Start 24-hour grace period immediately
        shopRepository.save(shop);

        log.info("Shop approved: {} (ID: {})", shop.getShopName(), shopId);

        // Auto-create Razorpay Linked Account only if they registered for the PRO plan.
        if ("PRO".equalsIgnoreCase(shop.getIntendedPlan())) {
            if (shop.getRazorpayLinkedAccountId() == null || shop.getRazorpayLinkedAccountId().isEmpty()) {
                try {
                    String linkedAccountId = razorpayService.createLinkedAccount(
                            shop.getShopName(),
                            shop.getEmail(),
                            shop.getPhone(),
                            shop.getBankAccountNumber(),
                            shop.getIfscCode(),
                            shop.getAccountHolderName(),
                            shop.getPanNumber() != null ? shop.getPanNumber() : "PENDING",
                            shop.getBusinessType() != null ? shop.getBusinessType() : "individual");

                    shop.setRazorpayLinkedAccountId(linkedAccountId);
                    shopRepository.save(shop);

                    if (linkedAccountId != null && !linkedAccountId.startsWith("acc_pending_")) {
                        razorpayService.addBankAccountToLinkedAccount(
                                linkedAccountId,
                                shop.getBankAccountNumber(),
                                shop.getIfscCode(),
                                shop.getAccountHolderName());
                    }
                    log.info("Razorpay Linked Account created for PRO shop on approval: {}", linkedAccountId);
                } catch (Exception e) {
                    log.error("Failed to auto-create Razorpay Linked Account during approval for shop {}: {}", shopId, e.getMessage());
                }
            }
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "Shop '" + shop.getShopName() + "' has been approved. " +
                "They can now purchase a subscription to activate their account.");

        emailService.sendShopApprovedEmail(
                shop.getEmail(),
                shop.getOwnerFullName(),
                shop.getShopName(),
                appBaseUrl);

        return response;
    }

    /**
     * Rejects a pending shop registration with a reason.
     *
     * Purpose: Super Admin rejects a shop with one of the preset reasons or a custom reason.
     * Input: The shop ID and the rejection reason.
     * Output: Map with success message. Shop status remains PENDING but reason is saved.
     */
    @Transactional
    public Map<String, String> rejectShop(Long shopId, String reason) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with ID: " + shopId));

        if (!"PENDING".equals(shop.getStatus())) {
            throw new InvalidRequestException("This shop is not in PENDING status.");
        }

        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("A rejection reason is required.");
        }

        shop.setStatus("REJECTED");
        shop.setRejectionReason(reason.trim());
        shopRepository.save(shop);

        log.info("Shop rejected: {} (ID: {}) — Reason: {}", shop.getShopName(), shopId, reason);

        emailService.sendShopRejectedEmail(
                shop.getEmail(),
                shop.getOwnerFullName(),
                shop.getShopName(),
                reason.trim(),
                appBaseUrl);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Shop '" + shop.getShopName() + "' registration has been rejected.");
        return response;
    }

    /**
     * Suspends an active shop.
     *
     * Purpose: Super Admin blocks a shop owner's login and all their customers' logins.
     * Input: The shop ID to suspend.
     * Output: Map with success message. All data remains intact.
     */
    @Transactional
    public Map<String, String> suspendShop(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with ID: " + shopId));

        shop.setStatus("SUSPENDED");
        shopRepository.save(shop);

        log.info("Shop suspended: {} (ID: {})", shop.getShopName(), shopId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Shop '" + shop.getShopName() + "' has been suspended. " +
                "The shop owner and all their customers are now blocked from logging in.");
        return response;
    }

    /**
     * Reactivates a suspended shop.
     *
     * Purpose: Super Admin restores a shop owner's access.
     * Input: The shop ID to reactivate.
     */
    @Transactional
    public Map<String, String> reactivateShop(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with ID: " + shopId));

        if (!"SUSPENDED".equals(shop.getStatus())) {
            throw new InvalidRequestException("Only suspended shops can be reactivated.");
        }

        shop.setStatus("APPROVED"); // Or active, but APPROVED starts the grace period check cleanly
        shopRepository.save(shop);

        log.info("Shop reactivated: {} (ID: {})", shop.getShopName(), shopId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Shop '" + shop.getShopName() + "' has been reactivated successfully.");
        return response;
    }

    /**
     * Completely deletes a suspended shop and all associated data.
     *
     * Purpose: Hard delete a shop. Cascades to customers, loans, payments, etc.
     * Input: The shop ID to delete.
     */
    @Transactional
    public Map<String, String> deleteShop(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with ID: " + shopId));

        if (!"SUSPENDED".equals(shop.getStatus())) {
            throw new InvalidRequestException("For safety, only suspended shops can be deleted.");
        }

        com.trustledgersaas.entity.User shopUser = shop.getUser();

        shopRepository.delete(shop);
        
        if (shopUser != null) {
            userRepository.delete(shopUser);
        }

        log.info("Shop and associated data fully deleted: {} (ID: {})", shop.getShopName(), shopId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Shop '" + shop.getShopName() + "' and all its data have been permanently deleted.");
        return response;
    }

    /**
     * Activates a shop's subscription after payment.
     *
     * Purpose: After a shop owner pays for a subscription, activate their account.
     * Input: Shop ID, plan type (BASIC/PRO), and payment details.
     * Output: Map with success message. Shop status becomes ACTIVE.
     */
    @Transactional
    public Map<String, String> activateSubscription(Long shopId, String planType,
                                                     String razorpayPaymentId, String razorpayOrderId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with ID: " + shopId));

        // Set subscription details
        shop.setStatus("ACTIVE");
        shop.setPlan(planType);
        if (shop.getSubscriptionStartDate() == null) {
            shop.setSubscriptionStartDate(LocalDate.now());
        }
        
        LocalDate currentExpiry = shop.getSubscriptionExpiryDate();
        if (currentExpiry != null && currentExpiry.isAfter(LocalDate.now())) {
            shop.setSubscriptionExpiryDate(currentExpiry.plusMonths(1));
        } else {
            shop.setSubscriptionExpiryDate(LocalDate.now().plusMonths(1));
        }
        
        shop.setSubscriptionStatus("ACTIVE");
        shopRepository.save(shop);

        // Record the subscription payment
        BigDecimal amount = "PRO".equals(planType) ? new BigDecimal("699.00") : new BigDecimal("299.00");
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .shop(shop)
                .amount(amount)
                .planType(planType)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpayOrderId(razorpayOrderId)
                .build();
        subscriptionPaymentRepository.save(payment);

        // If they just bought PRO, ensure they have a Razorpay Linked Account
        if ("PRO".equalsIgnoreCase(planType) && (shop.getRazorpayLinkedAccountId() == null || shop.getRazorpayLinkedAccountId().isEmpty())) {
            try {
                String linkedAccountId = razorpayService.createLinkedAccount(
                        shop.getShopName(),
                        shop.getEmail(),
                        shop.getPhone(),
                        shop.getBankAccountNumber(),
                        shop.getIfscCode(),
                        shop.getAccountHolderName(),
                        shop.getPanNumber() != null ? shop.getPanNumber() : "PENDING",
                        shop.getBusinessType() != null ? shop.getBusinessType() : "individual");

                shop.setRazorpayLinkedAccountId(linkedAccountId);
                shopRepository.save(shop);

                if (linkedAccountId != null && !linkedAccountId.startsWith("acc_pending_")) {
                    razorpayService.addBankAccountToLinkedAccount(
                            linkedAccountId,
                            shop.getBankAccountNumber(),
                            shop.getIfscCode(),
                            shop.getAccountHolderName());
                }
                log.info("Razorpay Linked Account created for PRO upgrade: {}", linkedAccountId);
            } catch (Exception e) {
                log.error("Failed to auto-create Razorpay Linked Account during PRO upgrade for shop {}: {}", shopId, e.getMessage());
            }
        }

        log.info("Subscription activated for shop: {} — Plan: {}", shop.getShopName(), planType);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Subscription activated! Your " + planType + " plan is now active until " +
                shop.getSubscriptionExpiryDate() + ".");
        return response;
    }

    /**
     * Extends a shop's subscription (Super Admin grace period action).
     *
     * Purpose: Manually extend a shop's subscription from the Super Admin panel.
     * Input: Shop ID and number of days to extend.
     * Output: Map with success message.
     */
    @Transactional
    public Map<String, String> extendSubscription(Long shopId, int days) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with ID: " + shopId));

        LocalDate currentExpiry = shop.getSubscriptionExpiryDate();
        if (currentExpiry == null) {
            currentExpiry = LocalDate.now();
        }

        // Extend from whichever is later: current expiry or today
        LocalDate newExpiry = currentExpiry.isAfter(LocalDate.now())
                ? currentExpiry.plusDays(days)
                : LocalDate.now().plusDays(days);

        shop.setSubscriptionExpiryDate(newExpiry);
        shop.setSubscriptionStatus("ACTIVE");
        if ("EXPIRED".equals(shop.getStatus()) || "APPROVED".equals(shop.getStatus())) {
            shop.setStatus("ACTIVE");
        }
        shopRepository.save(shop);

        log.info("Subscription extended for shop: {} — New expiry: {}", shop.getShopName(), newExpiry);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Subscription extended for '" + shop.getShopName() + "' until " + newExpiry + ".");
        return response;
    }

    // ==================== PLATFORM ANALYTICS ====================

    /**
     * Gets platform-wide analytics for the Super Admin dashboard.
     *
     * Purpose: Show overview stats: total shops, active shops, pending, etc.
     * Input: None.
     * Output: Map with various count and sum values.
     */
    public Map<String, Object> getPlatformAnalytics() {
        Map<String, Object> analytics = new HashMap<>();

        analytics.put("totalShops", shopRepository.count());
        analytics.put("activeShops", shopRepository.countByStatus("ACTIVE"));
        analytics.put("pendingApprovals", shopRepository.countByStatus("PENDING"));
        analytics.put("suspendedShops", shopRepository.countByStatus("SUSPENDED"));
        analytics.put("expiredSubscriptions", shopRepository.countBySubscriptionStatus("EXPIRED"));
        analytics.put("totalCustomers", customerRepository.count());
        analytics.put("totalActiveLoans", goldLoanRepository.countByStatus("ACTIVE"));
        analytics.put("totalOverdueLoans", goldLoanRepository.countByStatus("OVERDUE"));
        analytics.put("totalLoanVolume", goldLoanRepository.sumAllLoanAmounts());

        // This month's subscription revenue
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);
        analytics.put("monthlyRevenue", subscriptionPaymentRepository.sumPaymentsInMonth(startOfMonth, startOfNextMonth));

        return analytics;
    }
}
