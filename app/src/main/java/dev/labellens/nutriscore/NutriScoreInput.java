package dev.labellens.nutriscore;

/**
 * Everything the Nutri-Score needs, per 100 g (or 100 ml).
 * Any value may be null ("not printed"); the calculator decides what that means.
 *
 * @param type                    GENERAL, DRINK, CHEESE or OIL (only GENERAL is scored in v1)
 * @param fruitVegLegumesPercent  % of fruit, vegetables and legumes; almost never printed on labels
 */
public record NutriScoreInput(
        String type,
        Double energyKj,
        Double sugars,
        Double saturatedFat,
        Double salt,
        Double fibre,
        Double protein,
        Double fruitVegLegumesPercent
) {
}
