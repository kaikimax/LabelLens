package dev.labellens.product;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the API returns. A separate record instead of the entity itself, so the
 * JSON format doesn't change by accident when the database tables change.
 */
public record ProductResponse(
        UUID id,
        ProductStatus status,
        Instant createdAt,
        List<Photo> photos
) {

    public record Photo(int position, String contentType, long sizeBytes) {
    }

    static ProductResponse from(Product product) {
        List<Photo> photos = product.getPhotos().stream()
                .map(p -> new Photo(p.getPosition(), p.getContentType(), p.getSizeBytes()))
                .toList();
        return new ProductResponse(product.getId(), product.getStatus(), product.getCreatedAt(), photos);
    }
}
