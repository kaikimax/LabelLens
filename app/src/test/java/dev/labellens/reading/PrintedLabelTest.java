package dev.labellens.reading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrintedLabelTest {

    static PrintedLabel label(String type, String basis, Double energy, String energyUnit, Double fat) {
        return new PrintedLabel("Edam", "Le Berger", type, basis, null, energy, energyUnit, fat,
                null, null, null, null, null, null, null, null, "lait pasteurisé, ferments lactiques, sel.", "fr", null);
    }

    @Test
    void noNutritionTable_nullBasisIsAccepted_andDoesNotCrash() {
        // p031: Set.of(...).contains(null) threw a NullPointerException here.
        PrintedLabel p = label("CHEESE", null, null, null, null);

        assertThat(p.noNutritionTable()).isTrue();
        assertThat(p.problems()).isEmpty();
    }

    @Test
    void nutritionValues_withoutBasis_isAProblem_notACrash() {
        PrintedLabel p = label("GENERAL", null, 1088.4, "kJ", 0.3);

        assertThat(p.problems()).anyMatch(m -> m.contains("\"basis\""));
    }

    @Test
    void nullTypeOrUnit_isAProblem_notACrash() {
        assertThat(label(null, "100g", null, null, 0.3).problems()).anyMatch(m -> m.contains("\"type\""));
        assertThat(label("GENERAL", "100g", 1088.4, null, 0.3).problems()).anyMatch(m -> m.contains("\"energy_unit\""));
    }
}
