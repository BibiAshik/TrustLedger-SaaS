package com.trustledgersaas.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ShopRegisterRequestDTO — Carries shop registration form data from the frontend to AuthService.
 *
 * This DTO contains all the fields from the shop registration form.
 * File uploads (Aadhaar, PAN, owner photo, shop license) are handled separately
 * via MultipartFile parameters in the controller — they are NOT included in this DTO
 * because JSON doesn't support file uploads directly.
 *
 * Validation annotations enforce that required fields are provided and correctly formatted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopRegisterRequestDTO {

    // ==================== SHOP INFO ====================

    @NotBlank(message = "Shop name is required")
    @Size(min = 2, max = 100, message = "Shop name must be between 2 and 100 characters")
    private String shopName;

    @NotBlank(message = "Owner full name is required")
    @Size(min = 2, max = 100, message = "Owner name must be between 2 and 100 characters")
    private String ownerFullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 50, message = "Password must be between 6 and 50 characters")
    private String password;

    @NotBlank(message = "Shop address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Pincode is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be exactly 6 digits")
    private String pincode;

    // ==================== BANK DETAILS ====================

    @NotBlank(message = "Bank account number is required")
    private String bankAccountNumber;

    @NotBlank(message = "IFSC code is required")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Please provide a valid IFSC code")
    private String ifscCode;

    @NotBlank(message = "Account holder name is required")
    private String accountHolderName;

    /** UPI ID for receiving payments — required for online payment setup */
    private String upiId;

    // ==================== OPTIONAL FIELDS ====================

    /** GST number — optional, many small shops won't have this */
    private String gstNumber;

    // ==================== RAZORPAY REQUIRED FIELDS ====================

    /**
     * Owner's PAN number — required by Razorpay to create a Linked Account
     * for direct customer payment routing.
     */
    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN number format (e.g. ABCDE1234F)")
    private String panNumber;

    /**
     * Business type — required by Razorpay Route.
     * e.g. individual, proprietorship, partnership, private_limited, public_limited
     */
    @NotBlank(message = "Business type is required")
    private String businessType;
}
