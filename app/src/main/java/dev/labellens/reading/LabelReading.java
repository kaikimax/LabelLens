package dev.labellens.reading;

/**
 * The label converted to the project's standard: everything per 100 g (or 100 ml),
 * energy in kJ, salt in g. Same fields as labels.json, so the eval can compare them directly.
 *
 * Any value can be null: it means "not printed", which is different from 0.
 */
public record LabelReading(
        String name,
        String brand,
        String type,
        Double energyKj,
        Double fat,
        Double saturatedFat,
        Double carbohydrates,
        Double sugars,
        Double fibre,
        Double protein,
        Double salt,
        String ingredientsText,
        String ingredientsLanguage,
        /* What the printed values referred to before conversion: "100g", "100ml" or "serving". */
        String printedBasis,
        /* The "may contain" / traces sentence exactly as printed, or null. Kept out of ingredientsText. */
        String mayContainText
) {
}
