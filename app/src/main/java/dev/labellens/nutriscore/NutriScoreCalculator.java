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

    private NutriScoreCalculator() {
    }

    public static NutriScoreResult score(NutriScoreInput input) {
        // TODO (Oussama)
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
