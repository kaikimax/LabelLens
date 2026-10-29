package dev.labellens.foodeval;

import java.util.Set;

/**
 * What an allergen setup answers for one product, and the answer key in the same shape.
 *
 * @param mayContain null when it wasn't computed (the word list isn't available yet):
 *                   the scorer then skips the "may contain" checks instead of counting them wrong.
 */
public record AllergenAnswer(Set<String> allergens, Set<String> mayContain) {
}
