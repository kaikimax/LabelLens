package dev.labellens.eval;

import java.util.Objects;
import java.util.Set;

/**
 * One test case: an input for the AI step, the correct answer, and tags.
 *
 * Tags describe what makes the case special ("arabic", "blurry"...). The report gives
 * every score overall AND per tag, which is where findings like "salt drops to 70% on
 * Arabic labels" come from.
 *
 * @param <I> what the AI step receives (e.g. photos)
 * @param <E> the correct answer (e.g. the values typed by hand)
 */
public record EvalCase<I, E>(String id, I input, E expected, Set<String> tags) {

    public EvalCase {
        Objects.requireNonNull(id, "id");
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}
