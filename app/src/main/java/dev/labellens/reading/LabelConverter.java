package dev.labellens.reading;

/**
 * Turns the printed table into per-100 g values.
 *
 * The model never does arithmetic: it copies what's printed, and this class converts.
 * Code can't get a multiplication wrong; a language model can.
 */
public final class LabelConverter {

    static final double KCAL_TO_KJ = 4.184;
    static final double SODIUM_TO_SALT = 2.5;

    private LabelConverter() {
    }

    /** Assumes printed.problems() is empty (checked by LabelReader before calling this). */
    public static LabelReading toPer100g(PrintedLabel printed) {
        double factor = "serving".equals(printed.basis()) ? 100.0 / printed.servingSize() : 1.0;

        Double energyKj = printed.energy() == null ? null
                : scale("kcal".equals(printed.energyUnit()) ? printed.energy() * KCAL_TO_KJ : printed.energy(), factor);

        Double salt;
        if (printed.salt() != null) {
            salt = scale(printed.salt(), factor);
        } else if (printed.sodium() != null) {
            double sodiumGrams = "mg".equals(printed.sodiumUnit()) ? printed.sodium() / 1000.0 : printed.sodium();
            salt = scale(sodiumGrams * SODIUM_TO_SALT, factor);
        } else {
            salt = null;
        }

        return new LabelReading(
                printed.name(),
                printed.brand(),
                printed.type(),
                energyKj,
                scale(printed.fat(), factor),
                scale(printed.saturatedFat(), factor),
                scale(printed.carbohydrates(), factor),
                scale(printed.sugars(), factor),
                scale(printed.fibre(), factor),
                scale(printed.protein(), factor),
                salt,
                printed.ingredientsText(),
                printed.ingredientsLanguage(),
                printed.basis(),
                printed.mayContainText());
    }

    /** Multiplies and rounds to 3 decimals, keeping null as null. */
    private static Double scale(Double value, double factor) {
        if (value == null) {
            return null;
        }
        return Math.round(value * factor * 1000.0) / 1000.0;
    }
}
