package dev.labellens.reading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The nutrition table EXACTLY as printed, as the model returns it.
 * No conversion has happened yet: values may be per serving, energy may be in kcal,
 * and there may be sodium instead of salt. LabelConverter turns this into a LabelReading.
 *
 * Field names match the JSON format described in prompts/read-label.md.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrintedLabel(
        String name,
        String brand,
        String type,
        String basis,
        @JsonProperty("serving_size") Double servingSize,
        Double energy,
        @JsonProperty("energy_unit") String energyUnit,
        Double fat,
        @JsonProperty("saturated_fat") Double saturatedFat,
        Double carbohydrates,
        Double sugars,
        Double fibre,
        Double protein,
        Double salt,
        Double sodium,
        @JsonProperty("sodium_unit") String sodiumUnit,
        @JsonProperty("ingredients_text") String ingredientsText,
        @JsonProperty("ingredients_language") String ingredientsLanguage
) {

    static final Set<String> TYPES = Set.of("GENERAL", "DRINK", "CHEESE", "OIL");
    static final Set<String> BASES = Set.of("100g", "100ml", "serving");
    static final Set<String> ENERGY_UNITS = Set.of("kJ", "kcal");
    static final Set<String> SODIUM_UNITS = Set.of("g", "mg");
    static final Set<String> LANGUAGES = Set.of("fr", "ar", "en");

    /**
     * Problems that make this answer unusable. Empty list = OK.
     * These messages are sent back to the model when we ask it to try again,
     * so they must be clear and specific.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (!TYPES.contains(type)) {
            problems.add("\"type\" must be one of " + TYPES + ", got " + quote(type));
        }
        if (!BASES.contains(basis)) {
            problems.add("\"basis\" must be one of " + BASES + ", got " + quote(basis));
        }
        if ("serving".equals(basis) && (servingSize == null || servingSize <= 0)) {
            problems.add("\"basis\" is \"serving\" but \"serving_size\" is missing or not positive");
        }
        if (energy != null && !ENERGY_UNITS.contains(energyUnit)) {
            problems.add("\"energy_unit\" must be one of " + ENERGY_UNITS + " when energy is given, got " + quote(energyUnit));
        }
        if (sodium != null && !SODIUM_UNITS.contains(sodiumUnit)) {
            problems.add("\"sodium_unit\" must be one of " + SODIUM_UNITS + " when sodium is given, got " + quote(sodiumUnit));
        }
        if (ingredientsLanguage != null && !LANGUAGES.contains(ingredientsLanguage)) {
            problems.add("\"ingredients_language\" must be one of " + LANGUAGES + " or null, got " + quote(ingredientsLanguage));
        }
        return problems;
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
