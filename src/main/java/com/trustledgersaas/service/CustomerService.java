package com.trustledgersaas.service;

import com.trustledgersaas.dto.request.CustomerCreateRequestDTO;
import com.trustledgersaas.dto.response.CustomerResponseDTO;
import com.trustledgersaas.entity.Customer;
import com.trustledgersaas.entity.Shop;
import com.trustledgersaas.entity.User;
import com.trustledgersaas.exception.InvalidRequestException;
import com.trustledgersaas.exception.ResourceNotFoundException;
import com.trustledgersaas.mapper.CustomerMapper;
import com.trustledgersaas.repository.CustomerRepository;
import com.trustledgersaas.repository.GoldLoanRepository;
import com.trustledgersaas.repository.PaymentRepository;
import com.trustledgersaas.repository.ShopRepository;
import com.trustledgersaas.repository.UserRepository;
import com.trustledgersaas.util.InterestCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * CustomerService — Handles all customer management business logic.
 *
 * This includes:
 * - Creating customers (shop owner action) with auto-generated passwords
 * - Customer list with live search (name prefix matching)
 * - Customer detail view
 * - Customer limit enforcement (100 on Basic plan)
 * - Resetting customer passwords (shop owner action)
 */
@Service
@Slf4j
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoldLoanRepository goldLoanRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SmsService smsService;

    // ==================== CREATE CUSTOMER ====================

    /**
     * Creates a new customer under the given shop.
     *
     * Purpose: Shop owner adds a customer when they come to pledge gold.
     * Input: CustomerCreateRequestDTO (form data), file uploads, and the shop ID from JWT.
     * Output: Map with the customer's auto-generated login credentials.
     *
     * Flow:
     * 1. Check customer limit (100 on Basic plan)
     * 2. Check for duplicate phone/email within the same shop
     * 3. Auto-generate password: "GOLD" + last 4 digits of phone
     * 4. Create a User record (ROLE_CUSTOMER, isFirstLogin=true)
     * 5. Create a Customer record linked to the User and Shop
     * 6. Return credentials for the shop owner to relay to the customer
     */
    @Transactional
    public Map<String, Object> createCustomer(CustomerCreateRequestDTO dto, Long shopId,
                                               MultipartFile aadhaarFront, MultipartFile customerPhoto) {

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        // Step 1: Check customer limit on Basic plan
        long currentCustomerCount = customerRepository.countByShopId(shopId);
        if ("BASIC".equals(shop.getPlan()) && currentCustomerCount >= 100) {
            throw new InvalidRequestException("You have reached the 100 customer limit on your Basic plan. " +
                    "Upgrade to Pro to add more customers.");
        }

        // Step 2: Check for duplicate phone/email within this shop
        if (customerRepository.findByPhoneAndShopId(dto.getPhone(), shopId).isPresent()) {
            throw new InvalidRequestException("A customer with this phone number already exists in your shop.");
        }

        String customerEmail = normalizeEmail(dto.getEmail());
        if (customerEmail != null
                && customerRepository.findByEmailAndShopId(customerEmail, shopId).isPresent()) {
            throw new InvalidRequestException("A customer with this email already exists in your shop.");
        }

        // Step 3: Auto-generate password: "GOLD" + last 4 digits of phone
        String lastFourDigits = dto.getPhone().substring(dto.getPhone().length() - 4);
        String generatedPassword = "GOLD" + lastFourDigits;

        // Step 4: Create User record for the customer
        User user = User.builder()
                .email(buildCustomerLoginEmail(dto.getPhone(), shopId, customerEmail))
                .password(passwordEncoder.encode(generatedPassword))
                .fullName(dto.getFullName())
                .phone(dto.getPhone())
                .role("ROLE_CUSTOMER")
                .isFirstLogin(true) // Forces password change on first login
                .build();
        user = userRepository.save(user);

        // Step 5: Save uploaded files
        String aadhaarFrontPath = authService.saveFile(aadhaarFront, "customers/" + user.getId() + "/aadhaar");
        String customerPhotoPath = authService.saveFile(customerPhoto, "customers/" + user.getId() + "/photo");

        // Step 6: Create Customer record
        LocalDate dateOfBirth = null;
        if (dto.getDateOfBirth() != null && !dto.getDateOfBirth().isEmpty()) {
            dateOfBirth = LocalDate.parse(dto.getDateOfBirth());
        }

        Customer customer = Customer.builder()
                .fullName(dto.getFullName())
                .phone(dto.getPhone())
                .email(customerEmail)
                .dateOfBirth(dateOfBirth)
                .gender(dto.getGender())
                .address(dto.getAddress())
                .aadhaarNumber(dto.getAadhaarNumber())
                .aadhaarFrontPath(aadhaarFrontPath)
                .customerPhotoPath(customerPhotoPath)
                .panNumber(dto.getPanNumber())
                .shop(shop)
                .user(user)
                .build();
        customerRepository.save(customer);

        log.info("New customer created: {} (Phone: {}) for shop: {}",
                dto.getFullName(), dto.getPhone(), shop.getShopName());

        deliverCustomerCredentials(customerEmail, dto.getPhone(), dto.getFullName(),
                generatedPassword, shop.getShopName());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Customer created successfully! Login credentials have been sent via SMS"
                + (customerEmail != null ? " and email." : "."));
        response.put("customerId", customer.getId());
        response.put("generatedPassword", generatedPassword);
        response.put("customerName", dto.getFullName());
        response.put("phone", dto.getPhone());
        response.put("login", dto.getPhone());
        if (customerEmail != null) {
            response.put("email", customerEmail);
        }
        return response;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim();
    }

    /**
     * Internal login email for the User record when the customer did not provide one.
     */
    private String buildCustomerLoginEmail(String phone, Long shopId, String customerEmail) {
        if (customerEmail != null) {
            return customerEmail;
        }
        return phone + "+shop" + shopId + "@trustledger.local";
    }

    private void deliverCustomerCredentials(String customerEmail, String phone, String name,
                                            String password, String shopName) {
        smsService.sendCredentialsSms(phone, name, password, shopName);
        if (customerEmail != null) {
            emailService.sendCustomerCredentials(customerEmail, name, phone, password, shopName);
        }
    }

    // ==================== GET CUSTOMERS ====================

    /**
     * Gets all customers for a shop (paginated).
     *
     * Purpose: Shop owner views their customer list.
     * Input: Shop ID (from JWT) and Pageable for pagination.
     * Output: A Page of CustomerResponseDTOs.
     */
    public Page<CustomerResponseDTO> getCustomersByShopId(Long shopId, Pageable pageable) {
        Page<Customer> customers = customerRepository.findByShopId(shopId, pageable);
        return customers.map(customer -> {
            CustomerResponseDTO dto = CustomerMapper.toDTO(customer);
            // Add loan counts
            dto.setActiveLoanCount(goldLoanRepository.countByShopIdAndStatus(shopId, "ACTIVE"));
            return dto;
        });
    }

    /**
     * Searches customers by name prefix within a shop.
     *
     * Purpose: Live search bar on the customer list page — matches names
     *          that START WITH the typed letters, updating with every keystroke.
     * Input: Shop ID, the search prefix, and Pageable.
     * Output: A Page of matching CustomerResponseDTOs.
     */
    public Page<CustomerResponseDTO> searchCustomersByName(Long shopId, String namePrefix, Pageable pageable) {
        Page<Customer> customers = customerRepository.findByShopIdAndFullNameStartingWithIgnoreCase(
                shopId, namePrefix, pageable);
        return customers.map(CustomerMapper::toDTO);
    }

    /**
     * Gets a single customer's full details.
     *
     * Purpose: Shop owner or customer views a specific customer profile.
     * Input: Customer ID.
     * Output: CustomerResponseDTO with full details.
     */
    public CustomerResponseDTO getCustomerById(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        return buildCustomerDetailDTO(customer);
    }

    public CustomerResponseDTO getCustomerById(Long customerId, Long shopId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));
        if (!customer.getShop().getId().equals(shopId)) {
            throw new InvalidRequestException("This customer does not belong to your shop.");
        }

        return buildCustomerDetailDTO(customer);
    }

    private CustomerResponseDTO buildCustomerDetailDTO(Customer customer) {
        CustomerResponseDTO dto = CustomerMapper.toDTO(customer);
        long activeLoanCount = goldLoanRepository.countByCustomerIdAndStatus(customer.getId(), "ACTIVE");
        dto.setActiveLoanCount(activeLoanCount);
        return dto;
    }

    /**
     * Gets a customer by their User ID (from JWT token).
     *
     * Purpose: Customer views their own profile after login.
     * Input: User ID from the JWT token.
     * Output: CustomerResponseDTO.
     */
    public CustomerResponseDTO getCustomerByUserId(Long userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for this user"));
        return CustomerMapper.toDTO(customer);
    }

    // ==================== RESET CUSTOMER PASSWORD ====================

    /**
     * Resets a customer's password (shop owner action).
     *
     * Purpose: Shop owner resets a customer's password when they forget it.
     * Input: Customer ID.
     * Output: Map with the new generated password for the shop owner to relay.
     */
    @Transactional
    public Map<String, String> resetCustomerPassword(Long customerId, Long shopId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        // Verify the customer belongs to the shop owner's shop
        if (!customer.getShop().getId().equals(shopId)) {
            throw new InvalidRequestException("This customer does not belong to your shop.");
        }

        // Generate new password
        String lastFourDigits = customer.getPhone().substring(customer.getPhone().length() - 4);
        String newPassword = "GOLD" + lastFourDigits;

        // Update the user's password
        User user = customer.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFirstLogin(true); // Force password change on next login
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        Shop shop = customer.getShop();
        deliverCustomerCredentials(customer.getEmail(), customer.getPhone(),
                customer.getFullName(), newPassword, shop.getShopName());

        log.info("Password reset for customer: {} (ID: {})", customer.getFullName(), customerId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Password has been reset for " + customer.getFullName());
        response.put("newPassword", newPassword);
        return response;
    }
}
