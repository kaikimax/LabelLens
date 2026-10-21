package dev.labellens.product;

import dev.labellens.reading.LabelReading;
import dev.labellens.reading.LabelReadingEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the API returns. A separate record instead of the entities themselves, so the
 * JSON format doesn't change by accident when the database tables change.
 */
public record ProductResponse(
        UUID id,
        ProductStatus status,
        Instant createdAt,
        String failureReason,
        List<Photo> photos,
        LabelReading reading,
        List<String> warnings,
        String model
) {

    public record Photo(int position, String contentType, long sizeBytes) {
    }

    /** @param reading may be null (not read yet, or reading failed) */
    static ProductResponse from(Product product, LabelReadingEntity reading) {
        List<Photo> photos = product.getPhotos().stream()
                .map(p -> new Photo(p.getPosition(), p.getContentType(), p.getSizeBytes()))
                .toList();
        return new ProductResponse(
                product.getId(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getFailureReason(),
                photos,
                reading == null ? null : reading.toReading(),
                reading == null ? List.of() : reading.getWarnings(),
                reading == null ? null : reading.getModel());
    }
}
