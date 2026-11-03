package dev.labellens.product;

import dev.labellens.allergens.AllergenOutcome;
import dev.labellens.allergens.AllergenResultEntity;
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
        NutriScore nutriScore,
        Allergens allergens
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

    /**
     * @param ingredientsRead false: no ingredients list was read, so an empty {@code contains} means
     *                        "not checked", never "no allergens". A UI must say so.
     * @param contains        allergens in the ingredients: word list + AI (EU-14 tags, sorted)
     * @param mayContain      from the printed "may contain" sentence only
     * @param aiChecked       false: only the word list looked (AI failed, see aiProblem); it misses more
     * @param foundByAi       null when the AI gave no usable answer
     */
    public record Allergens(
            boolean ingredientsRead,
            List<String> contains,
            List<String> mayContain,
            boolean aiChecked,
            List<String> foundByWordList,
            List<String> foundByAi,
            String aiModel,
            String aiProblem
    ) {
        static Allergens from(AllergenResultEntity entity) {
            AllergenOutcome o = entity.toOutcome();
            return new Allergens(o.ingredientsRead(), List.copyOf(o.contains()), List.copyOf(o.mayContain()),
                    o.aiChecked(), List.copyOf(o.foundByWordList()),
                    o.foundByAi() == null ? null : List.copyOf(o.foundByAi()), o.aiModel(), o.aiProblem());
        }
    }

    /**
     * reading, nutriScore and allergens may be null (not read yet, reading failed, or a product
     * processed before allergens were added to the app).
     */
    static ProductResponse from(Product product, LabelReadingEntity reading, NutriScoreEntity nutriScore,
                                AllergenResultEntity allergens) {
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
                nutriScore == null ? null : NutriScore.from(nutriScore),
                allergens == null ? null : Allergens.from(allergens));
    }
}
