package com.trustledgersaas.mapper;

import com.trustledgersaas.dto.response.ShopResponseDTO;
import com.trustledgersaas.entity.Shop;

/**
 * ShopMapper — Converts between Shop entity and ShopResponseDTO.
 *
 * Plain manual field-by-field mapping. No MapStruct or auto-mapping library used.
 * This makes the code easy to read and trace through for a learner.
 */
public class ShopMapper {

    /**
     * Converts a Shop entity to a ShopResponseDTO for frontend display.
     *
     * Purpose: Transform database entity into a safe DTO for the frontend.
     * Input: A Shop entity loaded from the database.
     * Output: A ShopResponseDTO with the shop's displayable information.
     */
    public static ShopResponseDTO toDTO(Shop shop) {
        return ShopResponseDTO.builder()
                .id(shop.getId())
                .shopName(shop.getShopName())
                .ownerFullName(shop.getOwnerFullName())
                .ownerPhotoPath(formatUrl(shop.getOwnerPhotoPath()))
                .aadhaarDocumentPath(formatUrl(shop.getAadhaarDocumentPath()))
                .panPath(formatUrl(shop.getPanPath()))
                .shopLicensePath(formatUrl(shop.getShopLicensePath()))
                .panNumber(shop.getPanNumber())
                .businessType(shop.getBusinessType())
                .phone(shop.getPhone())
                .email(shop.getEmail())
                .address(shop.getAddress())
                .city(shop.getCity())
                .pincode(shop.getPincode())
                .bankAccountNumber(shop.getBankAccountNumber())
                .ifscCode(shop.getIfscCode())
                .accountHolderName(shop.getAccountHolderName())
                .upiId(shop.getUpiId())
                .gstNumber(shop.getGstNumber())
                .status(shop.getStatus())
                .plan(shop.getPlan())
                .intendedPlan(shop.getIntendedPlan())
                .subscriptionStartDate(shop.getSubscriptionStartDate())
                .subscriptionExpiryDate(shop.getSubscriptionExpiryDate())
                .subscriptionStatus(shop.getSubscriptionStatus())
                .rejectionReason(shop.getRejectionReason())
                .razorpayLinkedAccountId(shop.getRazorpayLinkedAccountId())
                .createdAt(shop.getCreatedAt())
                .build();
    }

    /**
     * Converts a ShopResponseDTO with counts — used when we also need to
     * show customer count and loan counts alongside the shop info.
     *
     * Purpose: Build a DTO that includes dashboard-style aggregate counts.
     * Input: A Shop entity, plus counts from repository queries.
     * Output: A ShopResponseDTO with all fields + counts populated.
     */
    public static ShopResponseDTO toDTOWithCounts(Shop shop,
                                                   long customerCount,
                                                   long activeLoanCount,
                                                   long overdueLoanCount) {
        ShopResponseDTO dto = toDTO(shop);
        dto.setCustomerCount(customerCount);
        dto.setActiveLoanCount(activeLoanCount);
        dto.setOverdueLoanCount(overdueLoanCount);
        return dto;
    }

    /**
     * Prefixes the relative file path with /uploads/ to make it a browser-usable URL.
     */
    private static String formatUrl(String path) {
        if (path == null || path.isEmpty()) return null;
        return path.startsWith("/uploads/") ? path : "/uploads/" + path;
    }
}
