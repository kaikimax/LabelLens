package dev.labellens.nutriscore;

/**
 * Either a score (scored = true: grade, score and points are set),
 * or "not scored" (scored = false: only notScoredReason is set).
 *
 * @param grade  "A" to "E"
 * @param score  negative points minus counted positive points
 */
public record NutriScoreResult(
        boolean scored,
        String notScoredReason,
        String grade,
        Integer score,
        NutriScorePoints points
) {
}
