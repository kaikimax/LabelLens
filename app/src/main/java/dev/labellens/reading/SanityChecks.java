package dev.labellens.reading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plausibility checks on a reading. They catch many misread numbers, because one wrong
 * digit usually breaks a rule that real labels follow.
 *
 * Each problem becomes a WARNING (the reading is still saved), never a failure:
 * real labels sometimes break these rules themselves (see p008, the lactose-free milk).
 *
 * Rules (docs/PLAN.md, day 3, step 5.2):
 *   a. No value above 100 g per 100 g.
 *   b. Sugars not above carbohydrates.
 *   c. Saturated fat not above fat.
 *   d. Energy matches the macros (protein*17 + carbohydrates*17 + fat*37 + fibre*8 kJ), within 20%.
 *   e. Null ("not printed") never causes a warning or a crash.
 *   f. When both salt and sodium are printed, salt = sodium x 2.5 (within rounding). Added on day 5:
 *      on p007 the model invented "salt: 0" next to "sodium: 170 mg", and the grade went from E to A.
 *
 * Known limit: a misread that moves a value into the wrong field AND leaves another one null
 * can pass every check (p009, run 2). The checks catch some errors; the eval measures the rest.
 */
public final class SanityChecks {

    static final double KJ_PER_G_PROTEIN = 17;
    static final double KJ_PER_G_CARBOHYDRATE = 17;
    static final double KJ_PER_G_FAT = 37;
    static final double KJ_PER_G_FIBRE = 8;
    static final double ENERGY_TOLERANCE = 0.20;
    static final double MIN_ENERGY_TO_CHECK_KJ = 40;
    static final double ROUNDING_TOLERANCE_G = 0.05;
    static final double SALT_SODIUM_TOLERANCE = 0.20;
    static final double SALT_SODIUM_MIN_TOLERANCE_G = 0.1;

    private SanityChecks() {
    }

    /** Returns human-readable warnings; an empty list means everything looks plausible. */
    public static List<String> check(LabelReading r) {
        List<String> warnings = new ArrayList<>();
        checkNothingAbove100g(r, warnings);
        checkPartNotAboveWhole("sugars", r.sugars(), "carbohydrates", r.carbohydrates(), warnings);
        checkPartNotAboveWhole("saturated fat", r.saturatedFat(), "fat", r.fat(), warnings);
        checkEnergyMatchesMacros(r, warnings);
        return warnings;
    }

    /**
     * All the checks, including those that need the table as printed (rule f: salt and sodium
     * are separate fields before conversion, and only salt is left after it).
     */
    public static List<String> check(LabelReading r, PrintedLabel printed) {
        List<String> warnings = check(r);
        if (printed != null) {
            checkSaltMatchesSodium(printed, warnings);
        }
        return warnings;
    }

    /**
     * Rule f. Compared on the printed basis (both values are per the same 100 g or serving).
     * Tolerance: 0.1 g or 20%, whichever is larger, because salt and sodium are rounded separately
     * on the label (sodium often to 5 or 10 mg).
     */
    static void checkSaltMatchesSodium(PrintedLabel p, List<String> warnings) {
        if (p.salt() == null || p.sodium() == null) {
            return; // rule e; and a label printing only one of them is normal
        }
        double sodiumGrams = "mg".equals(p.sodiumUnit()) ? p.sodium() / 1000.0 : p.sodium();
        double saltFromSodium = sodiumGrams * LabelConverter.SODIUM_TO_SALT;
        double difference = Math.abs(p.salt() - saltFromSodium);
        double allowed = Math.max(SALT_SODIUM_MIN_TOLERANCE_G, SALT_SODIUM_TOLERANCE * Math.max(p.salt(), saltFromSodium));
        if (difference > allowed) {
            warnings.add(String.format(Locale.ROOT, "salt (%.2f g) doesn't match sodium (%s %s = about %.2f g salt)",
                    p.salt(), formatPlain(p.sodium()), p.sodiumUnit(), saltFromSodium));
        }
    }

    /** "170" rather than "170.0", "0.7" stays "0.7". */
    private static String formatPlain(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** Rule a. A LinkedHashMap (not Map.of) because Map.of refuses null values, and ours can be null. */
    private static void checkNothingAbove100g(LabelReading r, List<String> warnings) {
        Map<String, Double> grams = new LinkedHashMap<>();
        grams.put("fat", r.fat());
        grams.put("saturated fat", r.saturatedFat());
        grams.put("carbohydrates", r.carbohydrates());
        grams.put("sugars", r.sugars());
        grams.put("fibre", r.fibre());
        grams.put("protein", r.protein());
        grams.put("salt", r.salt());

        grams.forEach((field, value) -> {
            if (value != null && value > 100) {
                warnings.add(field + " (" + format(value) + " g) is above 100 g per 100 g");
            }
        });
    }

    /** Rules b and c: a part (sugars, saturated fat) can't be more than its whole. */
    private static void checkPartNotAboveWhole(String partName, Double part, String wholeName, Double whole,
                                               List<String> warnings) {
        if (part == null || whole == null) {
            return; // rule e: can't compare with a missing value
        }
        if (part > whole + ROUNDING_TOLERANCE_G) {
            warnings.add(partName + " (" + format(part) + " g) is higher than " + wholeName
                    + " (" + format(whole) + " g)");
        }
    }

    /** Rule d. */
    private static void checkEnergyMatchesMacros(LabelReading r, List<String> warnings) {
        if (r.energyKj() == null || r.fat() == null || r.carbohydrates() == null) {
            return; // not enough information
        }
        double energy = r.energyKj();
        double expected = orZero(r.protein()) * KJ_PER_G_PROTEIN
                + r.carbohydrates() * KJ_PER_G_CARBOHYDRATE
                + r.fat() * KJ_PER_G_FAT
                + orZero(r.fibre()) * KJ_PER_G_FIBRE;

        if (energy < MIN_ENERGY_TO_CHECK_KJ && expected < MIN_ENERGY_TO_CHECK_KJ) {
            return; // salt, water...: tiny numbers make percentages meaningless
        }
        double difference = Math.abs(energy - expected) / Math.max(energy, expected);
        if (difference > ENERGY_TOLERANCE) {
            warnings.add("energy (" + format(energy) + " kJ) doesn't match the nutrients, which add up to about "
                    + format(expected) + " kJ (" + Math.round(difference * 100) + "% apart)");
        }
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }

    /** Locale.ROOT: always "4.7", never "4,7", whatever the computer's language settings. */
    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
