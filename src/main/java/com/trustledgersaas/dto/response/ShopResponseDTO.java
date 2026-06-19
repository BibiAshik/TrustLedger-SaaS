package com.trustledgersaas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ShopResponseDTO — Carries shop data from the backend to the frontend.
 *
 * Used in: Super Admin shop list, shop detail view, shop owner settings page.
 * Aadhaar-related data is NOT included here — it's handled separately with masking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopResponseDTO {

    private Long id;
    private String shopName;
    private String ownerFullName;
    private String ownerPhotoPath;
    private String aadhaarFrontPath;
    private String aadhaarBackPath;
    private String panPath;
    private String shopLicensePath;
    private String panNumber;
    private String businessType;
    private String phone;
    private String email;
    private String address;
    private String city;
    private String pincode;
    private String bankAccountNumber;
    private String ifscCode;
    private String accountHolderName;
    private String upiId;
    private String gstNumber;
    private String status;
    private String plan;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionExpiryDate;
    private String subscriptionStatus;
    private String rejectionReason;

    /** Razorpay Route linked account ID — set when shop is approved or upgrades to PRO */
    private String razorpayLinkedAccountId;

    private LocalDateTime createdAt;

    /** Count of customers belonging to this shop */
    private long customerCount;

    /** Count of active loans */
    private long activeLoanCount;

    /** Count of overdue loans */
    private long overdueLoanCount;
}
