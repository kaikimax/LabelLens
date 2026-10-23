package dev.labellens.nutriscore;

import dev.labellens.reading.LabelReading;

/**
 * Connects what the AI read to the calculator.
 *
 * Labels almost never print the fruit/vegetable/legumes %, and LabelReading has no field for it,
 * so it's passed as null, which the calculator counts as 0. That's a known limitation:
 * fruit-based products can get a worse grade than the official one (see docs/notes/day4-formula-check.md).
 */
public final class NutriScoreFromReading {

    private NutriScoreFromReading() {
    }

    public static NutriScoreInput toInput(LabelReading reading) {
        return new NutriScoreInput(
                reading.type(),
                reading.energyKj(),
                reading.sugars(),
                reading.saturatedFat(),
                reading.salt(),
                reading.fibre(),
                reading.protein(),
                null);
    }

    /** Always true for now: the fruit/vegetable % is never read from the label. */
    public static boolean fruitVegAssumedZero() {
        return true;
    }
}
