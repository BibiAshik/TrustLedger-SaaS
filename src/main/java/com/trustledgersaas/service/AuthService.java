package com.trustledgersaas.service;

import com.trustledgersaas.dto.request.LoginRequestDTO;
import com.trustledgersaas.dto.request.ShopRegisterRequestDTO;
import com.trustledgersaas.entity.Customer;
import com.trustledgersaas.entity.Shop;
import com.trustledgersaas.entity.User;
import com.trustledgersaas.exception.InvalidRequestException;
import com.trustledgersaas.exception.ResourceNotFoundException;
import com.trustledgersaas.repository.CustomerRepository;
import com.trustledgersaas.repository.ShopRepository;
import com.trustledgersaas.repository.UserRepository;
import com.trustledgersaas.security.JwtUtil;
import com.trustledgersaas.util.FileUploadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AuthService — Handles all authentication-related business logic.
 *
 * This includes:
 * - Shop Owner registration (with file uploads)
 * - Login for all 3 roles (Shop Owner, Customer, Super Admin)
 * - Account lockout after 5 failed attempts (12-hour lock)
 * - Customer multi-shop login disambiguation (Section 6.6)
 * - Forgot password / password reset (for Shop Owner and Super Admin)
 * - First-login forced password change (for Customers)
 */
@Service
@Slf4j
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private EmailService emailService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    // ==================== SHOP OWNER REGISTRATION ====================

    /**
     * Registers a new Shop Owner account.
     *
     * Purpose: Create a new User (ROLE_SHOP_OWNER) and Shop record from the registration form.
     * Input: ShopRegisterRequestDTO (form data) + MultipartFile uploads (Aadhaar, PAN, photo).
     * Output: A map containing a success message. The shop starts in PENDING status.
     *
     * Flow:
     * 1. Check if email already exists (prevent duplicate accounts)
     * 2. Create a User record with ROLE_SHOP_OWNER and BCrypt-hashed password
     * 3. Save uploaded files to the local filesystem
     * 4. Create a Shop record linked to the User, with status = PENDING
     * 5. Return success — the shop now awaits Super Admin approval
     */
    @Transactional
    public Map<String, Object> registerShopOwner(ShopRegisterRequestDTO dto,
                                                  MultipartFile ownerPhoto,
                                                  MultipartFile aadhaarDocument,
                                                  MultipartFile panDocument,
                                                  MultipartFile shopLicense) {

        // Step 1: Check if email is already registered
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new InvalidRequestException("An account with this email already exists. Please login instead.");
        }

        // Step 2: Create the User record
        User user = User.builder()
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .fullName(dto.getOwnerFullName())
                .phone(dto.getPhone())
                .role("ROLE_SHOP_OWNER")
                .isFirstLogin(false)
                .build();
        user = userRepository.save(user);

        // Step 3: Save uploaded files and get their paths
        String ownerPhotoPath = FileUploadUtil.saveFile(ownerPhoto, "shops/" + user.getId() + "/owner");
        String aadhaarDocumentPath = FileUploadUtil.saveFile(aadhaarDocument, "shops/" + user.getId() + "/aadhaar");
        String panPath = FileUploadUtil.saveFile(panDocument, "shops/" + user.getId() + "/pan");
        String shopLicensePath = shopLicense != null && !shopLicense.isEmpty()
                ? FileUploadUtil.saveFile(shopLicense, "shops/" + user.getId() + "/license") : null;

        // Step 4: Create the Shop record
        Shop shop = Shop.builder()
                .shopName(dto.getShopName())
                .ownerFullName(dto.getOwnerFullName())
                .ownerPhotoPath(ownerPhotoPath)
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .address(dto.getAddress())
                .city(dto.getCity())
                .pincode(dto.getPincode())
                .aadhaarDocumentPath(aadhaarDocumentPath)
                .panPath(panPath)
                .panNumber(dto.getPanNumber())
                .businessType(dto.getBusinessType())
                .bankAccountNumber(dto.getBankAccountNumber())
                .ifscCode(dto.getIfscCode())
                .accountHolderName(dto.getAccountHolderName())
                .upiId(dto.getUpiId())
                .gstNumber(dto.getGstNumber())
                .shopLicensePath(shopLicensePath)
                .status("PENDING")
                .plan("BASIC")
                .intendedPlan(dto.getIntendedPlan())
                .subscriptionStatus("NONE")
                .user(user)
                .build();
        shopRepository.save(shop);

        log.info("New shop registered: {} (Owner: {}) — Status: PENDING", dto.getShopName(), dto.getOwnerFullName());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Registration successful! Your shop is now pending approval from our team. " +
                "You will receive an email once your account is approved.");
        response.put("email", dto.getEmail());
        response.put("redirectUrl", "/application-status?email=" + dto.getEmail());
        return response;
    }

    // ==================== SHOP OWNER / SUPER ADMIN LOGIN ====================

    /**
     * Handles login for Shop Owners and Super Admin.
     *
     * Purpose: Authenticate a user by email + password and return a JWT token.
     * Input: LoginRequestDTO with email as identifier and password.
     * Output: A map containing JWT token, role, redirect URL, and user info.
     *
     * Flow:
     * 1. Check if account is locked (too many failed attempts)
     * 2. Find the user by email
     * 3. Verify the password using BCrypt
     * 4. Check shop status (for Shop Owners: PENDING, SUSPENDED, EXPIRED checks)
     * 5. Generate and return a JWT token
     */
    @Transactional
    public Map<String, Object> loginShopOwnerOrAdmin(LoginRequestDTO dto) {

        // Step 1: Find the user by email
        User user = userRepository.findByEmail(dto.getIdentifier())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Step 2: Check if account is locked
        checkAccountLock(user);

        // Step 3: Verify password
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            handleFailedLogin(user);
            int remaining = 5 - user.getFailedLoginAttempts();
            if (remaining <= 0) {
                throw new LockedException("Too many failed attempts. Your account is locked. " +
                        "Please contact your shop owner, or try again after 12 hours.");
            }
            throw new BadCredentialsException("Invalid email or password. " + remaining + " attempts remaining.");
        }

        // Step 4: Successful login — reset failed attempts
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Step 5: Role-specific checks
        Long shopId = null;
        String redirectUrl = "/";

        if ("ROLE_SHOP_OWNER".equals(user.getRole())) {
            // Check shop status
            Shop shop = shopRepository.findByUser(user)
                    .orElseThrow(() -> new ResourceNotFoundException("Shop not found for this user"));

            // Redirect pending/rejected shop owners to the application status page
            if ("PENDING".equals(shop.getStatus()) || "REJECTED".equals(shop.getStatus())) {
                Map<String, Object> response = new HashMap<>();
                response.put("applicationStatus", true);
                response.put("email", user.getEmail());
                response.put("shopName", shop.getShopName());
                response.put("status", shop.getStatus());
                response.put("applicationViewed", shop.isApplicationViewed());
                response.put("rejectionReason", shop.getRejectionReason());
                response.put("redirectUrl", "/application-status?email=" + user.getEmail());
                return response;
            }

            // Block login if shop is SUSPENDED
            if ("SUSPENDED".equals(shop.getStatus())) {
                throw new InvalidRequestException("This shop is currently inactive. Please contact support.");
            }

            // We no longer block login for expired subscriptions here.
            // A shop owner must be able to log in to access the Dashboard and Settings to renew.
            // The SubscriptionInterceptor will block them from accessing other features if the grace period has passed.

            shopId = shop.getId();
            redirectUrl = "/shop/dashboard";

        } else if ("ROLE_SUPER_ADMIN".equals(user.getRole())) {
            redirectUrl = "/admin/dashboard";
        }

        // Step 6: Generate JWT token
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId(), shopId);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("role", user.getRole());
        response.put("userId", user.getId());
        response.put("fullName", user.getFullName());
        response.put("email", user.getEmail());
        response.put("phone", user.getPhone());
        response.put("redirectUrl", redirectUrl);
        if (shopId != null) {
            response.put("shopId", shopId);
        }
        return response;
    }

    /**
     * Returns public application status for a shop owner who registered but is not yet active.
     */
    public Map<String, Object> getShopApplicationStatus(String email) {
        Shop shop = shopRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No shop registration found for this email address."));

        Map<String, Object> response = new HashMap<>();
        response.put("shopName", shop.getShopName());
        response.put("ownerFullName", shop.getOwnerFullName());
        response.put("email", shop.getEmail());
        response.put("status", shop.getStatus());
        response.put("applicationViewed", shop.isApplicationViewed());
        response.put("rejectionReason", shop.getRejectionReason());
        response.put("createdAt", shop.getCreatedAt());
        response.put("currentStep", resolveApplicationStep(shop));
        return response;
    }

    private int resolveApplicationStep(Shop shop) {
        if ("APPROVED".equals(shop.getStatus()) || "ACTIVE".equals(shop.getStatus())) {
            return 3;
        }
        if ("REJECTED".equals(shop.getStatus())) {
            return 3;
        }
        if (shop.isApplicationViewed()) {
            return 2;
        }
        return 1;
    }

    // ==================== CUSTOMER LOGIN ====================

    /**
     * Handles customer login with multi-shop disambiguation (Section 6.6).
     *
     * Purpose: Authenticate a customer who may have accounts at multiple shops.
     * Input: LoginRequestDTO with phone OR email as identifier, password, and optional selectedShopId.
     * Output: JWT token (if single match or shop selected), or a list of shops to choose from.
     *
     * Flow:
     * 1. Determine if input is email (contains "@") or phone (10 digits)
     * 2. Find ALL matching customer records across all shops
     * 3. Verify password against each match
     * 4. If exactly 1 match → log in directly
     * 5. If multiple matches → return shop list for disambiguation
     * 6. If selectedShopId is provided → log into that specific shop
     */
    @Transactional
    public Map<String, Object> loginCustomer(LoginRequestDTO dto) {
        String identifier = dto.getIdentifier();
        boolean isEmail = identifier.contains("@");

        // Step 1: Find all matching customers across all shops
        List<Customer> matchingCustomers;
        if (isEmail) {
            matchingCustomers = customerRepository.findAllByEmail(identifier);
        } else {
            matchingCustomers = customerRepository.findAllByPhone(identifier);
        }

        // Step 2: No matches found
        if (matchingCustomers.isEmpty()) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Step 3: Verify password against each matching customer's User account
        List<Customer> passwordMatchedCustomers = new ArrayList<>();
        for (Customer customer : matchingCustomers) {
            User customerUser = customer.getUser();

            // Check if account is locked
            if (customerUser.getLockedUntil() != null &&
                    customerUser.getLockedUntil().isAfter(LocalDateTime.now())) {
                throw new LockedException("Too many failed attempts. Your account is locked. " +
                        "Please contact your shop owner, or try again after 12 hours.");
            }

            if (passwordEncoder.matches(dto.getPassword(), customerUser.getPassword())) {
                passwordMatchedCustomers.add(customer);
            }
        }

        // Step 4: No password matches
        if (passwordMatchedCustomers.isEmpty()) {
            // Increment failed attempts on the first matching user
            User firstUser = matchingCustomers.get(0).getUser();
            handleFailedLogin(firstUser);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Step 5: If a specific shop was selected (disambiguation step 2)
        if (dto.getSelectedShopId() != null) {
            for (Customer customer : passwordMatchedCustomers) {
                if (customer.getShop().getId().equals(dto.getSelectedShopId())) {
                    return completeCustomerLogin(customer);
                }
            }
            throw new BadCredentialsException("Invalid email or password");
        }

        // Step 6: Exactly one match — log in directly
        if (passwordMatchedCustomers.size() == 1) {
            return completeCustomerLogin(passwordMatchedCustomers.get(0));
        }

        // Step 7: Multiple matches — return shop list for disambiguation
        Map<String, Object> response = new HashMap<>();
        response.put("multipleShops", true);
        response.put("message", "We found multiple accounts linked to this number. Please select your shop:");

        List<Map<String, Object>> shopList = new ArrayList<>();
        for (Customer customer : passwordMatchedCustomers) {
            Map<String, Object> shopInfo = new HashMap<>();
            shopInfo.put("shopId", customer.getShop().getId());
            shopInfo.put("shopName", customer.getShop().getShopName());
            shopInfo.put("shopCity", customer.getShop().getCity());
            shopList.add(shopInfo);
        }
        response.put("shops", shopList);

        return response;
    }

    /**
     * Completes the customer login process after disambiguation (if needed).
     *
     * Purpose: Verify shop status, generate JWT, and return login response.
     * Input: The specific Customer record to log in as.
     * Output: Map with JWT token and customer info.
     */
    private Map<String, Object> completeCustomerLogin(Customer customer) {
        Shop shop = customer.getShop();
        User user = customer.getUser();

        // Check shop plan — customer portal requires PRO plan
        if (!"PRO".equals(shop.getPlan())) {
            throw new InvalidRequestException("Customer portal access is currently unavailable for this shop. " +
                    "Please contact " + shop.getShopName() + " directly regarding your loan.");
        }

        // Check shop status
        if ("SUSPENDED".equals(shop.getStatus())) {
            throw new InvalidRequestException("This shop is currently inactive. Please contact them directly.");
        }

        // Check subscription status
        // Customers are blocked from logging in ONLY if the 24-hour grace period has fully expired.
        if (shop.getSubscriptionExpiryDate() != null && 
            java.time.LocalDate.now().isAfter(shop.getSubscriptionExpiryDate().plusDays(1))) {
            throw new InvalidRequestException("This shop's subscription has expired. " +
                    "Please contact " + shop.getShopName() + " directly regarding your loan.");
        }

        // Reset failed login attempts on successful login
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId(), shop.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("role", user.getRole());
        response.put("userId", user.getId());
        response.put("fullName", user.getFullName());
        response.put("email", user.getEmail());
        response.put("phone", user.getPhone());
        response.put("isFirstLogin", user.isFirstLogin());
        response.put("redirectUrl", user.isFirstLogin() ? "/customer/change-password" : "/customer/dashboard");
        response.put("shopId", shop.getId());
        response.put("shopName", shop.getShopName());
        return response;
    }

    // ==================== PASSWORD MANAGEMENT ====================

    /**
     * Handles the forced password change on first customer login.
     *
     * Purpose: Replace the auto-generated initial password with a customer-chosen one.
     * Input: User ID (from JWT), new password.
     * Output: Map with success message and new JWT token.
     */
    @Transactional
    public Map<String, Object> changePasswordFirstLogin(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);
        userRepository.save(user);

        // Get shop ID for the new token
        Customer customer = customerRepository.findByUser(user).orElse(null);
        Long shopId = customer != null ? customer.getShop().getId() : null;

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId(), shopId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Password changed successfully!");
        response.put("token", token);
        response.put("redirectUrl", "/customer/dashboard");
        return response;
    }

    /**
     * Initiates the forgot-password flow for Shop Owners and Super Admin.
     *
     * Purpose: Generate a password reset token and send it via email.
     * Input: The user's email address.
     * Output: A map with a success message (always the same, to not reveal if email exists).
     */
    @Transactional
    public Map<String, Object> forgotPassword(String email) {
        Map<String, Object> response = new HashMap<>();
        // Always return the same message — don't reveal if the email exists or not
        response.put("message", "If an account with that email exists, a password reset link has been sent.");

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Only allow forgot password for Shop Owner and Super Admin roles
            if ("ROLE_CUSTOMER".equals(user.getRole())) {
                return response; // Silently ignore — customers use shop-owner-triggered reset
            }

            // Generate a random reset token
            String resetToken = UUID.randomUUID().toString();
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetExpiry(LocalDateTime.now().plusHours(1)); // Token valid for 1 hour
            userRepository.save(user);

            String resetLink = appBaseUrl + "/reset-password?token=" + resetToken;
            String subject = "Trust Ledger - Password Reset";
            String body = "Dear " + user.getFullName() + ",\n\n" +
                    "We received a request to reset your Trust Ledger password.\n\n" +
                    "Use this link within 1 hour:\n" + resetLink + "\n\n" +
                    "If you did not request this, you can ignore this email.\n\n" +
                    "Regards,\n" +
                    "Trust Ledger - Protect Gold. Preserve Trust.";
            emailService.sendEmail(user.getEmail(), subject, body);

            // TODO: Send email with reset link (will be implemented in Phase 4 - EmailService)
            log.info("Password reset token generated for user: {} — Token: {}", email, resetToken);
        }

        return response;
    }

    /**
     * Resets a user's password using a valid reset token.
     *
     * Purpose: Allow password change via the reset link clicked from email.
     * Input: The reset token and the new password.
     * Output: Map with success or error message.
     */
    @Transactional
    public Map<String, Object> resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new InvalidRequestException("Invalid or expired password reset link."));

        // Check if token has expired
        if (user.getPasswordResetExpiry() == null ||
                user.getPasswordResetExpiry().isBefore(LocalDateTime.now())) {
            throw new InvalidRequestException("This password reset link has expired. Please request a new one.");
        }

        // Reset the password
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);
        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Password reset successful! You can now log in with your new password.");
        return response;
    }

    // ==================== ACCOUNT LOCKOUT HELPERS ====================

    /**
     * Checks if a user account is currently locked.
     *
     * Purpose: Block login attempts on locked accounts without even checking the password.
     * Input: The User entity to check.
     * Output: Throws LockedException if locked; does nothing if not locked.
     */
    private void checkAccountLock(User user) {
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
                // Account is still locked
                throw new LockedException("Too many failed attempts. Your account is locked. " +
                        "Please contact your shop owner, or try again after 12 hours.");
            } else {
                // Lock period has passed — reset and allow login
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        }
    }

    /**
     * Increments the failed login counter and locks the account after 5 failures.
     *
     * Purpose: Implement the brute-force protection from Section 6.8.
     * Input: The User entity that failed to log in.
     * Output: Updates the user's failedLoginAttempts (and lockedUntil if threshold reached).
     */
    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        // Lock the account after 5 consecutive failures
        if (attempts >= 5) {
            user.setLockedUntil(LocalDateTime.now().plusHours(12));
            log.warn("Account locked for user: {} — too many failed login attempts", user.getEmail());
        }

        userRepository.save(user);
    }
}
