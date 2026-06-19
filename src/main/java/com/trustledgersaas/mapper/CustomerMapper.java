package com.trustledgersaas.mapper;

import com.trustledgersaas.dto.response.CustomerResponseDTO;
import com.trustledgersaas.entity.Customer;

/**
 * CustomerMapper — Converts between Customer entity and CustomerResponseDTO.
 *
 * IMPORTANT: Aadhaar number is ALWAYS MASKED in the DTO.
 * The full 12-digit number is stored in the database, but the DTO only
 * contains the masked version (e.g. "XXXX XXXX 1234").
 */
public class CustomerMapper {

    /**
     * Converts a Customer entity to a CustomerResponseDTO for frontend display.
     *
     * Purpose: Transform database entity into a safe DTO with masked Aadhaar.
     * Input: A Customer entity loaded from the database.
     * Output: A CustomerResponseDTO with masked Aadhaar number.
     */
    public static CustomerResponseDTO toDTO(Customer customer) {
        return CustomerResponseDTO.builder()
                .id(customer.getId())
                .fullName(customer.getFullName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .dateOfBirth(customer.getDateOfBirth())
                .gender(customer.getGender())
                .address(customer.getAddress())
                .maskedAadhaarNumber(maskAadhaarNumber(customer.getAadhaarNumber()))
                .aadhaarFrontPath(customer.getAadhaarFrontPath())
                .customerPhotoPath(customer.getCustomerPhotoPath())
                .panNumber(customer.getPanNumber())
                .shopName(customer.getShop() != null ? customer.getShop().getShopName() : null)
                .shopId(customer.getShop() != null ? customer.getShop().getId() : null)
                .createdAt(customer.getCreatedAt())
                .build();
    }

    /**
     * Masks an Aadhaar number so only the last 4 digits are visible.
     *
     * Purpose: Protect PII (Personally Identifiable Information) by never
     *          showing the full Aadhaar number on any screen.
     * Input: Full 12-digit Aadhaar number (e.g. "123456781234").
     * Output: Masked format: "XXXX XXXX 1234".
     *
     * This masking is applied EVERY time a Customer is sent to the frontend,
     * for ALL roles (including Super Admin).
     */
    public static String maskAadhaarNumber(String aadhaarNumber) {
        // If the Aadhaar number is null or too short, return a placeholder
        if (aadhaarNumber == null || aadhaarNumber.length() < 4) {
            return "XXXX XXXX XXXX";
        }

        // Get the last 4 digits
        String lastFourDigits = aadhaarNumber.substring(aadhaarNumber.length() - 4);

        // Return in the masked format: "XXXX XXXX 1234"
        return "XXXX XXXX " + lastFourDigits;
    }
}
