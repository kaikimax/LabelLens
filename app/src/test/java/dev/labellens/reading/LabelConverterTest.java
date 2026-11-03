package dev.labellens.reading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LabelConverterTest {

    private static PrintedLabel printed(String basis, Double servingSize, Double energy, String energyUnit,
                                        Double carbohydrates, Double salt, Double sodium, String sodiumUnit) {
        return new PrintedLabel("name", "brand", "GENERAL", basis, servingSize, energy, energyUnit,
                0.0, 0.0, carbohydrates, 1.0, null, 0.0, salt, sodium, sodiumUnit, "ingredients", "fr", null);
    }

    @Test
    void per100g_isKeptAsIs() {
        LabelReading r = LabelConverter.toPer100g(printed("100g", null, 1088.4, "kJ", 62.9, 0.05, null, null));

        assertThat(r.energyKj()).isEqualTo(1088.4);
        assertThat(r.carbohydrates()).isEqualTo(62.9);
        assertThat(r.salt()).isEqualTo(0.05);
        assertThat(r.printedBasis()).isEqualTo("100g");
    }

    @Test
    void kcal_isConvertedToKj() {
        LabelReading r = LabelConverter.toPer100g(printed("100ml", null, 100.0, "kcal", 1.0, 0.0, null, null));

        assertThat(r.energyKj()).isCloseTo(418.4, within(0.001));
    }

    @Test
    void usStyleServing_isScaledTo100ml_andSodiumBecomesSalt() {
        // p007, the Tabasco sauce: per 5 ml serving, 5 kcal, 2 g carbohydrates, 170 mg sodium.
        LabelReading r = LabelConverter.toPer100g(printed("serving", 5.0, 5.0, "kcal", 2.0, null, 170.0, "mg"));

        assertThat(r.energyKj()).isCloseTo(418.4, within(0.001));
        assertThat(r.carbohydrates()).isEqualTo(40.0);
        assertThat(r.sugars()).isEqualTo(20.0);
        assertThat(r.salt()).isCloseTo(8.5, within(0.001));
    }

    @Test
    void sodiumInGrams_becomesSalt() {
        LabelReading r = LabelConverter.toPer100g(printed("100g", null, 100.0, "kJ", 1.0, null, 0.4, "g"));

        assertThat(r.salt()).isCloseTo(1.0, within(0.001));
    }

    @Test
    void saltWins_whenBothSaltAndSodiumArePrinted() {
        LabelReading r = LabelConverter.toPer100g(printed("100g", null, 100.0, "kJ", 1.0, 0.7, 0.4, "g"));

        assertThat(r.salt()).isEqualTo(0.7);
    }

    @Test
    void nullsStayNull() {
        LabelReading r = LabelConverter.toPer100g(printed("serving", 30.0, null, null, null, null, null, null));

        assertThat(r.energyKj()).isNull();
        assertThat(r.carbohydrates()).isNull();
        assertThat(r.salt()).isNull();
        assertThat(r.fibre()).isNull();
    }
}
