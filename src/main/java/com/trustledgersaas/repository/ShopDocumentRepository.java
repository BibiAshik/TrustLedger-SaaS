package com.trustledgersaas.repository;

import com.trustledgersaas.entity.ShopDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * ShopDocumentRepository — Data access layer for the ShopDocument entity.
 *
 * Used by the Super Admin when reviewing a shop's registration to retrieve
 * all uploaded documents (Aadhaar, PAN, owner photo, etc.).
 */
@Repository
public interface ShopDocumentRepository extends JpaRepository<ShopDocument, Long> {

    /** Find all documents uploaded by a specific shop — used for Super Admin review */
    List<ShopDocument> findByShopId(Long shopId);

    /** Find a specific type of document for a shop (e.g. "AADHAAR_FRONT") */
    List<ShopDocument> findByShopIdAndDocumentType(Long shopId, String documentType);
}
