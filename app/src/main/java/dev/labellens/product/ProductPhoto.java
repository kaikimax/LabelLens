package dev.labellens.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One uploaded photo. The image itself is stored by PhotoStorage
 * (a local folder now, Azure Blob Storage later); the database keeps only its key.
 */
@Entity
@Table(name = "product_photo")
public class ProductPhoto {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Upload order: 1, 2 or 3. */
    @Column(nullable = false)
    private int position;

    /** Where PhotoStorage saved the file, e.g. "3f2a.../1.jpg". */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    protected ProductPhoto() {
    }

    ProductPhoto(Product product, int position, String storageKey, String contentType, long sizeBytes) {
        this.id = UUID.randomUUID();
        this.product = product;
        this.position = position;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
