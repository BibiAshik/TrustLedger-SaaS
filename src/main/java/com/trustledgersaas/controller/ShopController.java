package com.trustledgersaas.controller;

import com.trustledgersaas.dto.request.CustomerCreateRequestDTO;
import com.trustledgersaas.dto.response.CustomerResponseDTO;
import com.trustledgersaas.dto.response.PaymentResponseDTO;
import com.trustledgersaas.dto.response.ShopResponseDTO;
import com.trustledgersaas.security.JwtUtil;
import com.trustledgersaas.service.CustomerService;
import com.trustledgersaas.service.LoanService;
import com.trustledgersaas.service.PaymentService;
import com.trustledgersaas.service.ShopService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ShopController — Handles all Shop Owner dashboard and management endpoints.
 *
 * All endpoints require ROLE_SHOP_OWNER. The shop ID is extracted from the
 * JWT token (never from a URL parameter) to ensure data isolation — a shop
 * owner can only access their own shop's data.
 */
@RestController
@Slf4j
public class ShopController {

    @Autowired
    private ShopService shopService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private LoanService loanService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JwtUtil jwtUtil;

    // ==================== SHOP SETTINGS API ====================

    /** Gets the shop's settings/profile data */
    @GetMapping("/api/shop/settings")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ShopResponseDTO> getSettings(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(shopService.getShopByUserId(userId));
    }

    // ==================== SHOP DASHBOARD API ====================

    /** Gets the shop owner's own shop data for the dashboard */
    @GetMapping("/api/shop/dashboard")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<Map<String, Object>> getDashboardData(
            @RequestHeader("Authorization") String authHeader) {

        Long userId = extractUserId(authHeader);
        ShopResponseDTO shop = shopService.getShopByUserId(userId);

        // Get recent payments
        List<PaymentResponseDTO> recentPayments = paymentService.getRecentPayments(shop.getId(), 5);

        // Get loans due this week
        var loansDueThisWeek = loanService.getLoansDueThisWeek(shop.getId());

        Map<String, Object> dashboard = new java.util.HashMap<>();
        dashboard.put("shop", shop);
        dashboard.put("recentPayments", recentPayments);
        dashboard.put("loansDueThisWeek", loansDueThisWeek);
        dashboard.put("totalLoanVolume", loanService.getTotalLoanVolume(shop.getId()));

        return ResponseEntity.ok(dashboard);
    }

    // ==================== CUSTOMER MANAGEMENT API ====================

    /** Creates a new customer */
    @PostMapping("/api/shop/customers")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<Map<String, Object>> createCustomer(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CustomerCreateRequestDTO dto) {

        Long shopId = extractShopId(authHeader);
        Map<String, Object> result = customerService.createCustomer(dto, shopId, null);
        return ResponseEntity.ok(result);
    }

    /** Gets customers for this shop (paginated, with optional search) */
    @GetMapping("/api/shop/customers")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<Page<CustomerResponseDTO>> getCustomers(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {

        Long shopId = extractShopId(authHeader);
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());

        if (search != null && !search.isEmpty()) {
            return ResponseEntity.ok(customerService.searchCustomersByName(shopId, search, pageable));
        }
        return ResponseEntity.ok(customerService.getCustomersByShopId(shopId, pageable));
    }

    /** Gets a specific customer's details */
    @GetMapping("/api/shop/customers/{id}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<CustomerResponseDTO> getCustomerById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        Long shopId = extractShopId(authHeader);
        return ResponseEntity.ok(customerService.getCustomerById(id, shopId));
    }

    /** Resets a customer's password */
    @PostMapping("/api/shop/customers/{id}/reset-password")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<Map<String, String>> resetCustomerPassword(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        Long shopId = extractShopId(authHeader);
        return ResponseEntity.ok(customerService.resetCustomerPassword(id, shopId));
    }

    // ==================== HELPER METHODS ====================

    /**
     * Extracts the user ID from the JWT token in the Authorization header.
     */
    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7); // Remove "Bearer "
        return jwtUtil.extractUserId(token);
    }

    /**
     * Extracts the shop ID from the JWT token in the Authorization header.
     */
    private Long extractShopId(String authHeader) {
        String token = authHeader.substring(7);
        return jwtUtil.extractShopId(token);
    }
}
