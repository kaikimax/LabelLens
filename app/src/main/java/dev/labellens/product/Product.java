package dev.labellens.product;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One upload: a food product and its 1–3 photos.
 * Maps to the "product" table created in V1__create_product.sql.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    /** Why processing failed, in words a human understands. Null unless status is FAILED. */
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ProductPhoto> photos = new ArrayList<>();

    /** Required by JPA. Use {@link #create()} in your own code. */
    protected Product() {
    }

    public static Product create() {
        Product product = new Product();
        // The id is generated here, in Java, so we know it before saving
        // and can use it to name the photo files.
        product.id = UUID.randomUUID();
        product.createdAt = Instant.now();
        product.status = ProductStatus.PENDING;
        return product;
    }

    public void addPhoto(int position, String storageKey, String contentType, long sizeBytes) {
        photos.add(new ProductPhoto(this, position, storageKey, contentType, sizeBytes));
    }

    public void markExtracted() {
        this.status = ProductStatus.EXTRACTED;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = ProductStatus.FAILED;
        this.failureReason = reason == null || reason.length() <= 1000 ? reason : reason.substring(0, 1000);
    }

    public String getFailureReason() {
        return failureReason;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public List<ProductPhoto> getPhotos() {
        return List.copyOf(photos);
    }
}
