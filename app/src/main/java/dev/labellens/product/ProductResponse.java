package dev.labellens.product;

import dev.labellens.nutriscore.NutriScoreEntity;
import dev.labellens.nutriscore.NutriScorePoints;
import dev.labellens.nutriscore.NutriScoreResult;
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
        String model,
        NutriScore nutriScore
) {

    public record Photo(int position, String contentType, long sizeBytes) {
    }

    /**
     * @param fruitVegAssumedZero true when the fruit/vegetable % wasn't on the label and 0 was used:
     *                            the grade may then be worse than the official one.
     */
    public record NutriScore(
            boolean scored,
            String notScoredReason,
            String grade,
            Integer score,
            NutriScorePoints points,
            boolean fruitVegAssumedZero,
            String algorithmVersion
    ) {
        static NutriScore from(NutriScoreEntity entity) {
            NutriScoreResult r = entity.toResult();
            return new NutriScore(r.scored(), r.notScoredReason(), r.grade(), r.score(), r.points(),
                    entity.isFruitVegAssumedZero(), entity.getAlgorithmVersion());
        }
    }

    /** reading and nutriScore may be null (not read yet, or reading failed). */
    static ProductResponse from(Product product, LabelReadingEntity reading, NutriScoreEntity nutriScore) {
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
                reading == null ? null : reading.getModel(),
                nutriScore == null ? null : NutriScore.from(nutriScore));
    }
}
