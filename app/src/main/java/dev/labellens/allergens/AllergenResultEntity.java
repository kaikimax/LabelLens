package dev.labellens.allergens;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Row of the allergen_result table (V5__create_allergen_result.sql): one per product. */
@Entity
@Table(name = "allergen_result")
public class AllergenResultEntity {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "ingredients_read", nullable = false)
    private boolean ingredientsRead;

    /** Tag lists are comma-separated and sorted; '' = none (a tag never contains a comma). */
    @Column(nullable = false, columnDefinition = "text")
    private String contains;
    @Column(name = "may_contain", nullable = false, columnDefinition = "text")
    private String mayContain;
    @Column(name = "found_by_word_list", nullable = false, columnDefinition = "text")
    private String foundByWordList;
    /** Null when the AI gave no usable answer. */
    @Column(name = "found_by_ai", columnDefinition = "text")
    private String foundByAi;

    @Column(name = "ai_model", length = 100)
    private String aiModel;
    @Column(name = "ai_problem", length = 1000)
    private String aiProblem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AllergenResultEntity() {
    }

    public static AllergenResultEntity from(UUID productId, AllergenOutcome outcome) {
        AllergenResultEntity e = new AllergenResultEntity();
        e.productId = productId;
        e.ingredientsRead = outcome.ingredientsRead();
        e.contains = join(outcome.contains());
        e.mayContain = join(outcome.mayContain());
        e.foundByWordList = join(outcome.foundByWordList());
        e.foundByAi = outcome.foundByAi() == null ? null : join(outcome.foundByAi());
        e.aiModel = outcome.aiModel();
        e.aiProblem = abbreviate(outcome.aiProblem());
        e.createdAt = Instant.now();
        return e;
    }

    public AllergenOutcome toOutcome() {
        return new AllergenOutcome(ingredientsRead, split(contains), split(mayContain), split(foundByWordList),
                foundByAi == null ? null : split(foundByAi), aiModel, aiProblem);
    }

    private static String join(Collection<String> tags) {
        return String.join(",", new TreeSet<>(tags));
    }

    /** Sorted, so the API always lists the tags in the same order. */
    private static Set<String> split(String joined) {
        return joined.isEmpty() ? new TreeSet<>() : new TreeSet<>(Arrays.asList(joined.split(",")));
    }

    private static String abbreviate(String text) {
        return text == null || text.length() <= 1000 ? text : text.substring(0, 997) + "...";
    }
}
