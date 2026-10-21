package dev.labellens.reading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SanityChecks {

    static final double KJ_PER_G_PROTEIN = 17;
    static final double KJ_PER_G_CARBOHYDRATE = 17;
    static final double KJ_PER_G_FAT = 37;
    static final double KJ_PER_G_FIBRE = 8;
    static final double ENERGY_TOLERANCE = 0.20;
    static final double MIN_ENERGY_TO_CHECK_KJ = 40;
    static final double ROUNDING_TOLERANCE_G = 0.05;

    private SanityChecks() {
    }

    public static List<String> check(LabelReading r) {
        List<String> warnings = new ArrayList<>();
        checkNothingAbove100g(r, warnings);
        checkPartNotAboveWhole("sugars", r.sugars(), "carbohydrates", r.carbohydrates(), warnings);
        checkPartNotAboveWhole("saturated fat", r.saturatedFat(), "fat", r.fat(), warnings);
        checkEnergyMatchesMacros(r, warnings);
        return warnings;
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