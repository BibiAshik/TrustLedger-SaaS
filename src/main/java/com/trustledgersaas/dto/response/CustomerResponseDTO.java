package com.trustledgersaas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CustomerResponseDTO — Carries customer data from the backend to the frontend.
 *
 * IMPORTANT: Aadhaar number is always MASKED in this DTO (e.g. "XXXX XXXX 1234").
 * The full Aadhaar number is stored in the database but NEVER sent to any frontend page.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponseDTO {

    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private LocalDate dateOfBirth;
    private String gender;
    private String address;

    /**
     * Aadhaar number — ALWAYS MASKED as "XXXX XXXX 1234" (last 4 digits only).
     * The full number is never sent to the frontend, for any role, including Super Admin.
     */
    private String maskedAadhaarNumber;

    private String aadhaarFrontPath;
    private String customerPhotoPath;
    private String panNumber;
    private String shopName;
    private Long shopId;
    private LocalDateTime createdAt;

    /** Number of active loans this customer has */
    private long activeLoanCount;

    /** Total outstanding amount across all this customer's loans */
    private String totalOutstanding;
}
