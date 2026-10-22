package dev.labellens.nutriscore;

/**
 * The points for each component, so the app can explain WHY a product got its grade.
 *
 * protein holds the points from the protein table even when they don't count;
 * proteinCounted says whether they were included in the score.
 */
public record NutriScorePoints(
        int energy,
        int sugars,
        int saturatedFat,
        int salt,
        int protein,
        int fibre,
        int fruitVegLegumes,
        boolean proteinCounted
) {
}
