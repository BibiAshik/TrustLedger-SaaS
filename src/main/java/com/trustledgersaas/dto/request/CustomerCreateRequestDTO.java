package com.trustledgersaas.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerCreateRequestDTO — Carries customer creation form data from the Shop Owner dashboard
 * to CustomerService.
 *
 * Customers never self-register. The Shop Owner fills in this form when a customer comes
 * to pledge gold. File uploads (Aadhaar photo, customer photo) are handled as separate
 * MultipartFile parameters in the controller.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCreateRequestDTO {

    @NotBlank(message = "Customer full name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;

    /** Email is optional — phone is the primary login identifier */
    @Email(message = "Please provide a valid email address")
    private String email;

    /** Date of birth in ISO format (yyyy-MM-dd) */
    private String dateOfBirth;

    /** Gender: Male, Female, or Other */
    private String gender;

    /** Customer's residential address */
    private String address;

    @NotBlank(message = "Aadhaar number is required")
    @Pattern(regexp = "^[0-9]{12}$", message = "Aadhaar number must be exactly 12 digits")
    private String aadhaarNumber;

    /** PAN number — mandatory for all customers */
    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN number format")
    private String panNumber;
}
