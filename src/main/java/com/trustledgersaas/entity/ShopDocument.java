package com.trustledgersaas.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ShopDocument — Stores metadata about documents uploaded during shop registration.
 *
 * Each document (Aadhaar front, Aadhaar back, PAN, owner photo, shop license)
 * gets its own record here, linked back to the Shop that uploaded it.
 * The actual files are stored on the local filesystem; this entity only tracks
 * the file path and document type.
 *
 * Relationships:
 * - Many ShopDocuments belong to one Shop — @ManyToOne with Shop
 */
@Entity
@Table(name = "shop_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The type of document this record represents.
     * Possible values: AADHAAR_FRONT, AADHAAR_BACK, PAN, OWNER_PHOTO, SHOP_LICENSE
     */
    @Column(nullable = false)
    private String documentType;

    /** File system path where the uploaded document is stored */
    @Column(nullable = false)
    private String filePath;

    /** Original filename as uploaded by the user (for display purposes) */
    private String originalFileName;

    /**
     * The shop this document belongs to.
     * Many documents belong to one Shop (ManyToOne relationship).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    /** When this document was uploaded */
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
