package dev.labellens.foodeval;

import java.util.Locale;

/**
 * The comparison rule used by the real eval.
 *
 * ---------------------------------------------------------------------------------
 * Day 5, step 2: written by Oussama (docs/PLAN.md).
 * Rules are written in the decisions log (PLAN.md)
 * ---------------------------------------------------------------------------------
 */
public final class ReadingComparison implements FieldComparison {

    @Override
    public boolean sameNumber(String field, Double expected, Double actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;

        if (field.equals("energy_kj")) {
            return (actual >= (expected - expected*0.02) && actual <= (expected + expected*0.02)); 
        } else if (field.equals("fat") || field.equals("carbohydrates") || field.equals("sugars") || field.equals("fibre") || field.equals("protein") || field.equals("saturated_fat")) {
            if (expected < 10) {
                return (Math.abs(actual - expected) <=0.1+1e-9);
            } else {
                return (Math.abs(actual - expected) <=1+1e-9);
            }
        } else if (field.equals("salt")) {
            if (expected < 1) {
                return (Math.abs(actual - expected)<=0.01+1e-9);
            } else {
                return (Math.abs(actual - expected)<=0.1+1e-9);
            }
        }
        throw new UnsupportedOperationException("Invalid field name: "+field);
    }

    @Override
    public boolean sameText(String field, String expected, String actual) {
        if (field.equals("ingredients")) {
            if ((expected == null || expected.trim().isEmpty()) && (actual == null || actual.trim().isEmpty())) return true;
            if ((expected == null || expected.trim().isEmpty()) || (actual == null || actual.trim().isEmpty())) return false;

            if (!expected.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").replaceAll(" *([,;:.()%]) *", "$1").trim()
                .equals(
                    actual.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").replaceAll(" *([,;:.()%]) *", "$1").trim()
                )
            ) return false;
            return true;
        }
        throw new UnsupportedOperationException("Invalid field name: "+field);
    }
}
