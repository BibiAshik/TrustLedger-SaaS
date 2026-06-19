package com.trustledgersaas.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LoginRequestDTO — Carries login form data from the frontend to AuthService.
 *
 * Used for all three login types (Super Admin, Shop Owner, Customer).
 * The "identifier" field accepts either an email or a phone number
 * (for customer login, per Section 6.5).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequestDTO {

    /**
     * Login identifier — can be an email address OR a phone number.
     * For Shop Owners and Super Admin: always an email.
     * For Customers: either email (contains "@") or phone (10 digits).
     */
    @NotBlank(message = "Email or phone number is required")
    private String identifier;

    /** The user's password */
    @NotBlank(message = "Password is required")
    private String password;

    /**
     * If the customer has accounts at multiple shops and selected one,
     * this field carries the chosen shop ID.
     * Null on the initial login attempt; populated on the second attempt
     * after the user picks a shop from the disambiguation screen.
     */
    private Long selectedShopId;
}
