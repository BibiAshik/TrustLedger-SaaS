package com.trustledgersaas.controller;

import com.trustledgersaas.dto.response.ShopResponseDTO;
import com.trustledgersaas.dto.response.SubscriptionPaymentResponseDTO;
import com.trustledgersaas.entity.SubscriptionPayment;
import com.trustledgersaas.service.ShopService;
import com.trustledgersaas.repository.SubscriptionPaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SuperAdminController — Handles all Super Admin panel endpoints.
 *
 * The Super Admin panel is accessed via a hidden URL (/admin/login) that
 * is never linked from any public page.
 *
 * All endpoints require ROLE_SUPER_ADMIN (enforced at both URL level in
 * SecurityConfig and method level via @PreAuthorize).
 */
@Controller
@Slf4j
public class SuperAdminController {

    @Autowired
    private ShopService shopService;

    @Autowired
    private SubscriptionPaymentRepository subscriptionPaymentRepository;

    // ==================== API ENDPOINTS ====================

    /** Gets platform-wide analytics for the dashboard */
    @GetMapping("/api/admin/analytics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        return ResponseEntity.ok(shopService.getPlatformAnalytics());
    }

    /** Gets pending shops (approval queue) */
    @GetMapping("/api/admin/shops/pending")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Page<ShopResponseDTO>> getPendingShops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(shopService.getShopsByStatus("PENDING", pageable));
    }

    /** Gets all shops */
    @GetMapping("/api/admin/shops")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Page<ShopResponseDTO>> getAllShops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(shopService.getAllShops(pageable));
    }

    /** Gets a specific shop's details */
    @GetMapping("/api/admin/shops/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<ShopResponseDTO> getShopById(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.getShopById(id));
    }

    /** Gets the subscription revenue log for the Super Admin. */
    @GetMapping("/api/admin/subscriptions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Page<SubscriptionPaymentResponseDTO>> getSubscriptionPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<SubscriptionPayment> payments =
                subscriptionPaymentRepository.findAllByOrderByPaymentDateDesc(pageable);

        Page<SubscriptionPaymentResponseDTO> response = payments.map(payment ->
                SubscriptionPaymentResponseDTO.builder()
                        .id(payment.getId())
                        .shopId(payment.getShop().getId())
                        .shopName(payment.getShop().getShopName())
                        .amount(payment.getAmount())
                        .planType(payment.getPlanType())
                        .paymentDate(payment.getPaymentDate())
                        .razorpayPaymentId(payment.getRazorpayPaymentId())
                        .createdAt(payment.getCreatedAt())
                        .build()
        );

        return ResponseEntity.ok(response);
    }

    /** Approves a pending shop */
    @PostMapping("/api/admin/shops/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> approveShop(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.approveShop(id));
    }

    /** Rejects a pending shop with a reason */
    @PostMapping("/api/admin/shops/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> rejectShop(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        return ResponseEntity.ok(shopService.rejectShop(id, reason));
    }

    /** Suspends an active shop */
    @PostMapping("/api/admin/shops/{id}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> suspendShop(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.suspendShop(id));
    }

    /** Reactivates a suspended shop */
    @PostMapping("/api/admin/shops/{id}/reactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> reactivateShop(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.reactivateShop(id));
    }

    /** Deletes a suspended shop */
    @DeleteMapping("/api/admin/shops/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteShop(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.deleteShop(id));
    }

    /** Extends a shop's subscription */
    @PostMapping("/api/admin/subscriptions/{shopId}/extend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> extendSubscription(
            @PathVariable Long shopId,
            @RequestBody Map<String, Integer> body) {
        int days = body.getOrDefault("days", 30);
        return ResponseEntity.ok(shopService.extendSubscription(shopId, days));
    }
}
