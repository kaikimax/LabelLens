package dev.labellens.nutriscore;

/**
 * Nutri-Score 2023, general foods.
 *
 * ---------------------------------------------------------------------------------
 * Written by Oussama (docs/PLAN.md, day 4, step 3). An AI assistant must NOT write
 * this class: hints, pointing at a failing test and code review only.
 *
 * WHAT it must do (the tests in NutriScoreCalculatorTest say it precisely):
 *   - DRINK, CHEESE, OIL: not scored in v1 (scored = false, with a reason).
 *   - Missing energy, sugars, saturated fat or salt: not scored, the reason names the
 *     missing value. Missing fibre, protein or fruit/veg %: count as 0.
 *   - Negative points: energy, sugars, saturated fat, salt.
 *     Positive points: protein, fibre, fruit/vegetables/legumes.
 *   - Protein only counts when the negative points are below 11 (general rule).
 *   - score = negative - counted positive; the grade comes from the score.
 *
 * HOW (the point tables and grade thresholds): from the OFFICIAL 2023 specification
 * for general foods. Find it yourself; it's the only source of truth.
 *
 * Out of scope for v1: the red meat rule (protein capped), beverages, cheese, fats.
 * ---------------------------------------------------------------------------------
 */
public final class NutriScoreCalculator {
    
    private static final double[] ENERGYKJ = {335, 670, 1005, 1340, 1675, 2010, 2345, 2680, 3015, 3350};
    private static final double[] SUGARS = {3.4, 6.8, 10, 14, 17, 20, 24, 27, 31, 34, 37, 41, 44, 48, 51};
    private static final double[] SATURATEDFAT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    private static final double[] SALT = {0.2, 0.4, 0.6, 0.8, 1, 1.2, 1.4, 1.6, 1.8, 2, 2.2, 2.4, 2.6, 2.8, 3, 3.2, 3.4, 3.6, 3.8, 4};
    private static final double[] PROTEIN = {2.4, 4.8, 7.2, 9.6, 12, 14, 17};
    private static final double[] FIBRE = {3.0, 4.1, 5.2, 6.3, 7.4};

    private NutriScoreCalculator() {
    }

    public static NutriScoreResult score(NutriScoreInput input) {
        if ((input.type() != null) && (input.type().equals("DRINK") || input.type().equals("OIL") || input.type().equals("CHEESE"))) {
            return new NutriScoreResult(false, "Drinks, Oils and cheeses are not included in v1", null, null, null);
        }

        if (input.energyKj() == null) {
            return new NutriScoreResult(false, "energykj is missing", null, null, null);
        } else if (input.sugars() == null){
            return new NutriScoreResult(false, "sugars is missing", null, null, null);
        } else if (input.saturatedFat() == null) {
            return new NutriScoreResult(false, "saturatedFat is missing", null, null, null);
        } else if (input.salt() == null) {
            return new NutriScoreResult(false, "salt is missing", null, null, null);
        }

        int energyPoint = pointsFor(input.energyKj(), ENERGYKJ);
        int sugarsPoint = pointsFor(input.sugars(), SUGARS);
        int saturatedFatPoint = pointsFor(input.saturatedFat(), SATURATEDFAT);
        int saltPoint = pointsFor(input.salt(), SALT);
        int proteinPoint = input.protein() != null ? pointsFor(input.protein(), PROTEIN) : 0;
        int fibrePoint = input.fibre() != null ? pointsFor(input.fibre(), FIBRE) : 0;

        int fruitVegLegumesPercentPoint;
        if (input.fruitVegLegumesPercent() != null){
            if (input.fruitVegLegumesPercent() > 80) {
                fruitVegLegumesPercentPoint = 5;
            } else if (input.fruitVegLegumesPercent() > 60) {
                fruitVegLegumesPercentPoint = 2;
            } else if (input.fruitVegLegumesPercent() > 40) {
                fruitVegLegumesPercentPoint = 1;
            } else {
                fruitVegLegumesPercentPoint = 0;
            }
        } else {
            fruitVegLegumesPercentPoint = 0;
        }

        int scoreN = energyPoint + sugarsPoint + saturatedFatPoint + saltPoint;
        int scoreP;
        boolean doesProteinCount;
        if (scoreN < 11) {
            scoreP = proteinPoint + fibrePoint + fruitVegLegumesPercentPoint;
            doesProteinCount = true;
        } else {
            scoreP = fibrePoint + fruitVegLegumesPercentPoint;
            doesProteinCount = false;
        }
        int score = scoreN - scoreP;
        String grade;
        if (score <= 0) {grade = "A";}
        else if (score <= 2) {grade = "B";}
        else if (score <= 10) {grade = "C";}
        else if (score <= 18) {grade = "D";}
        else {grade = "E";}

        return new NutriScoreResult(true, null, grade, score, new NutriScorePoints(energyPoint, sugarsPoint, saturatedFatPoint, saltPoint, proteinPoint, fibrePoint, fruitVegLegumesPercentPoint, doesProteinCount));
    }

    private static int pointsFor(double value, double[] thresholds) {
        int counter = 0;
        for (double threshold : thresholds) {
            if (value > threshold) {
                counter++;
            } else {
                return counter;
            }
        }
        return counter;
    }
}
