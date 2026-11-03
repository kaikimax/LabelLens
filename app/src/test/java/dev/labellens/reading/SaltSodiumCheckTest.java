package dev.labellens.reading;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity rule f (day 5): when both salt and sodium are printed, salt = sodium x 2.5.
 * Found on p007: the model invented "salt: 0" next to "sodium: 170 mg", the converter used
 * the 0, and a very salty sauce got grade A instead of E without any warning.
 */
class SaltSodiumCheckTest {

    /** A table as the model returns it; only salt and sodium matter here. */
    static PrintedLabel printed(Double salt, Double sodium, String sodiumUnit) {
        return new PrintedLabel("Sauce", "Brand", "GENERAL", "100g", null,
                5.0, "kcal", 0.0, 0.0, 1.0, 0.0, 0.0, 0.0,
                salt, sodium, sodiumUnit, "water, salt", "en", null);
    }

    static List<String> warnings(PrintedLabel p) {
        return SanityChecks.check(LabelConverter.toPer100g(p), p);
    }

    @Test
    void p007_inventedSaltZero_nextToRealSodium_isFlagged() {
        List<String> w = warnings(printed(0.0, 170.0, "mg"));

        assertThat(w).hasSize(1);
        assertThat(w.getFirst()).isEqualTo("salt (0.00 g) doesn't match sodium (170 mg = about 0.43 g salt)");
    }

    @Test
    void saltAndSodiumThatAgree_giveNoWarning() {
        assertThat(warnings(printed(1.75, 0.7, "g"))).isEmpty();      // EU label printing both, in grams
        assertThat(warnings(printed(0.43, 170.0, "mg"))).isEmpty();   // same, sodium in mg
    }

    @Test
    void separateRounding_isTolerated() {
        // 170 mg sodium = 0.425 g salt; a label rounding salt to 0.4 is not a misread.
        assertThat(warnings(printed(0.4, 170.0, "mg"))).isEmpty();
        // Small values: 0.1 g of room, so 0.05 vs 0.0 is fine.
        assertThat(warnings(printed(0.05, 0.0, "g"))).isEmpty();
    }

    @Test
    void justOutsideTheTolerance_isFlagged() {
        // 0.7 g sodium = 1.75 g salt; 20% of 1.75 = 0.35, so 1.39 (0.36 apart) is flagged.
        assertThat(warnings(printed(1.39, 0.7, "g"))).hasSize(1);
        assertThat(warnings(printed(1.41, 0.7, "g"))).isEmpty();
    }

    @Test
    void onlySodium_orOnlySalt_isNormal() {
        assertThat(warnings(printed(null, 170.0, "mg"))).isEmpty();   // US label
        assertThat(warnings(printed(0.5, null, null))).isEmpty();     // EU label
        assertThat(warnings(printed(null, null, null))).isEmpty();    // not printed
    }

    @Test
    void theOtherChecksStillRun() {
        // Sugars above carbohydrates (rule b) AND salt/sodium mismatch (rule f): both reported.
        PrintedLabel p = new PrintedLabel("Sauce", "Brand", "GENERAL", "100g", null,
                5.0, "kcal", 0.0, 0.0, 1.0, 5.0, 0.0, 0.0,
                0.0, 170.0, "mg", "water, salt", "en", null);

        assertThat(warnings(p)).hasSize(2);
    }
}
