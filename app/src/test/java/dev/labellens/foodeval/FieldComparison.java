package dev.labellens.foodeval;

/**
 * The comparison rule: when does a value read by the AI count as correct?
 * Kept separate from the scorer so the rule can change without touching anything else,
 * and so the scorer's tests can use a simple stand-in rule.
 *
 * Field names are the ones used in labels.json: "energy_kj", "fat", "saturated_fat",
 * "carbohydrates", "sugars", "fibre", "protein", "salt", and "ingredients" for the text.
 */
public interface FieldComparison {

    /** Either value can be null: null means "not printed on the label". */
    boolean sameNumber(String field, Double expected, Double actual);

    /** Either value can be null. */
    boolean sameText(String field, String expected, String actual);
}
