package com.trustledgersaas.controller;

import com.trustledgersaas.dto.request.LoginRequestDTO;
import com.trustledgersaas.dto.request.ShopRegisterRequestDTO;
import com.trustledgersaas.security.JwtUtil;
import com.trustledgersaas.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * AuthController — Handles all authentication-related endpoints.
 *
 * This controller manages:
 * - Serving public pages (homepage, login, registration)
 * - API endpoints for login (all 3 roles), registration, password management
 *
 * Both page-serving (GET returning Thymeleaf views) and API endpoints
 * (POST/PUT returning JSON) coexist in this controller.
 */
@Controller
@Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    // ==================== API ENDPOINTS ====================
    // These return JSON responses for AJAX calls from the frontend

    /**
     * Registers a new shop owner.
     *
     * Handles multipart form data (text fields + file uploads).
     * Files are received as separate MultipartFile parameters because
     * JSON doesn't support binary file data.
     */
    @PostMapping("/api/auth/shop/register")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerShop(
            @Valid @ModelAttribute ShopRegisterRequestDTO dto,
            @RequestParam("ownerPhoto") MultipartFile ownerPhoto,
            @RequestParam("aadhaarFront") MultipartFile aadhaarFront,
            @RequestParam("aadhaarBack") MultipartFile aadhaarBack,
            @RequestParam("panCard") MultipartFile panDocument,
            @RequestParam(value = "shopLicense", required = false) MultipartFile shopLicense) {

        Map<String, Object> result = authService.registerShopOwner(dto, ownerPhoto,
                aadhaarFront, aadhaarBack, panDocument, shopLicense);
        return ResponseEntity.ok(result);
    }

    /**
     * Handles login for Shop Owners and Super Admin.
     *
     * Returns a JWT token on success, or error messages on failure.
     * The frontend stores the token and includes it in subsequent requests.
     */
    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequestDTO dto) {
        Map<String, Object> result = authService.loginShopOwnerOrAdmin(dto);
        return ResponseEntity.ok(result);
    }

    /**
     * Handles customer login with multi-shop disambiguation.
     *
     * May return a JWT token (single match) or a list of shops (multiple matches).
     */
    @PostMapping("/api/auth/customer/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> customerLogin(@Valid @RequestBody LoginRequestDTO dto) {
        Map<String, Object> result = authService.loginCustomer(dto);
        return ResponseEntity.ok(result);
    }

    /**
     * Forced password change on first customer login.
     */
    @PostMapping("/api/auth/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        String token = authHeader.substring(7);
        Long userId = jwtUtil.extractUserId(token);
        String newPassword = body.get("newPassword");

        Map<String, Object> result = authService.changePasswordFirstLogin(userId, newPassword);
        return ResponseEntity.ok(result);
    }

    /**
     * Initiates forgot password flow (Shop Owner / Super Admin only).
     */
    @PostMapping("/api/auth/forgot-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        Map<String, Object> result = authService.forgotPassword(email);
        return ResponseEntity.ok(result);
    }

    /**
     * Resets password using a valid reset token.
     */
    @PostMapping("/api/auth/reset-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");
        Map<String, Object> result = authService.resetPassword(token, newPassword);
        return ResponseEntity.ok(result);
    }

    /**
     * Public endpoint — shop owner checks registration approval progress.
     */
    @GetMapping("/api/auth/shop/application-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getShopApplicationStatus(
            @RequestParam String email) {
        Map<String, Object> result = authService.getShopApplicationStatus(email.trim());
        return ResponseEntity.ok(result);
    }
}
