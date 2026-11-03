package dev.labellens.allergens;

import java.util.Set;

/**
 * The allergens of one product, as the app reports them.
 *
 * @param ingredientsRead false when no ingredients list was read: then {@code contains} is empty
 *                        because nothing was checked, NOT because the product has no allergens
 * @param contains        word list ∪ AI (decisions log, 2026-10-26: a miss is the dangerous mistake)
 * @param mayContain      from the printed "may contain" sentence, word list only (never guessed)
 * @param foundByAi       null when the AI gave no usable answer; {@code aiProblem} then says why,
 *                        and {@code contains} is the word list alone
 */
public record AllergenOutcome(
        boolean ingredientsRead,
        Set<String> contains,
        Set<String> mayContain,
        Set<String> foundByWordList,
        Set<String> foundByAi,
        String aiModel,
        String aiProblem
) {

    /** True when both the word list and the AI looked at the ingredients. */
    public boolean aiChecked() {
        return ingredientsRead && foundByAi != null;
    }
}
