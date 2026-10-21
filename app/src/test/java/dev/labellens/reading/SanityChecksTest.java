package dev.labellens.reading;

// import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The specification for SanityChecks, written as tests.
 * Your job (Oussama): remove @Disabled, then implement SanityChecks.check() until all pass.
 *
 *     mvn -pl app test -Dtest=SanityChecksTest
 *
 * Real products from labels.json are used where possible.
 */
// @Disabled("Implement SanityChecks.check(), then remove this line")
class SanityChecksTest {

    /** Helper: a reading with only the nutrition values set. */
    private static LabelReading reading(Double energyKj, Double fat, Double saturatedFat, Double carbohydrates,
                                        Double sugars, Double fibre, Double protein, Double salt) {
        return new LabelReading("test", "test", "GENERAL", energyKj, fat, saturatedFat, carbohydrates,
                sugars, fibre, protein, salt, "ingredients", "fr", "100g");
    }

    // ---- Real products that are fine: no warnings ----

    @Test
    void strawberryJam_p004_hasNoWarnings() {
        // 0.47*17 + 62.9*17 + 0.3*37 = 1088 kJ, printed 1088.4
        var r = reading(1088.4, 0.3, 0.0, 62.9, 60.0, null, 0.47, 0.05);
        assertThat(SanityChecks.check(r)).isEmpty();
    }

    @Test
    void mayonnaise_p003_hasNoWarnings() {
        var r = reading(1599.4, 38.4, 6.3, 9.58, 0.41, null, 0.95, 1.75);
        assertThat(SanityChecks.check(r)).isEmpty();
    }

    @Test
    void salt_p001_hasNoWarnings_becauseNearZeroEnergyIsNotChecked() {
        var r = reading(0.0, 0.0, 0.0, 0.0, 0.0, null, 0.0, 99.0);
        assertThat(SanityChecks.check(r)).isEmpty();
    }

    @Test
    void lactoseFreeMilk_p008_passes_eventhoughTheLabelIsProbablyWrong() {
        // Sugars printed as 0 with 4.7 g carbohydrates: suspicious, but sugars <= carbohydrates holds.
        // This shows the limits of the checks: they can't catch every label error.
        var r = reading(243.7, 3.0, 1.3, 4.7, 0.0, null, 3.0, 0.0);
        assertThat(SanityChecks.check(r)).isEmpty();
    }

    // ---- Rule a: nothing above 100 g ----

    @Test
    void valueAbove100g_warns() {
        var r = reading(1500.0, 10.0, 2.0, 50.0, 20.0, 3.0, 8.0, 120.0);
        assertThat(SanityChecks.check(r)).anyMatch(w -> w.toLowerCase().contains("salt"));
    }

    @Test
    void energyAbove100_isNotAGramValue_soNoWarningForThat() {
        // 3700 kJ is normal for oil; it must not trigger rule a.
        var r = reading(3700.0, 100.0, 14.6, null, null, null, null, 0.0);
        assertThat(SanityChecks.check(r)).noneMatch(w -> w.toLowerCase().contains("energy") && w.contains("100"));
    }

    // ---- Rule b: sugars <= carbohydrates ----

    @Test
    void sugarsAboveCarbohydrates_warns() {
        var r = reading(406.8, 3.0, 2.1, 9.4, 12.0, null, 8.0, 0.0);
        assertThat(SanityChecks.check(r)).anyMatch(w -> w.toLowerCase().contains("sugars"));
    }

    @Test
    void sugarsEqualToCarbohydratesWithRounding_doesNotWarn() {
        var r = reading(1020.0, 0.0, 0.0, 60.0, 60.04, null, 0.0, 0.0);
        assertThat(SanityChecks.check(r)).noneMatch(w -> w.toLowerCase().contains("sugars"));
    }

    // ---- Rule c: saturated fat <= fat ----

    @Test
    void saturatedFatAboveFat_warns() {
        var r = reading(406.8, 2.0, 3.1, 9.4, 6.5, null, 8.0, 0.0);
        assertThat(SanityChecks.check(r)).anyMatch(w -> w.toLowerCase().contains("saturated"));
    }

    // ---- Rule d: energy matches the macros ----

    @Test
    void kcalTypedAsKj_warnsAboutEnergy() {
        // p011 soy sauce: 17 was kcal, not kJ. Macros add up to ~97 kJ.
        var r = reading(17.0, 0.0, null, 2.4, 2.0, null, 3.3, 6.46);
        assertThat(SanityChecks.check(r)).anyMatch(w -> w.toLowerCase().contains("energy"));
    }

    @Test
    void misreadDigit_warnsAboutEnergy() {
        // Jam with carbohydrates misread as 6.29 instead of 62.9.
        var r = reading(1088.4, 0.3, 0.0, 6.29, 6.0, null, 0.47, 0.05);
        assertThat(SanityChecks.check(r)).anyMatch(w -> w.toLowerCase().contains("energy"));
    }

    @Test
    void energyWithin20Percent_doesNotWarn() {
        // Tomato sauce p006: macros give ~198 kJ, printed 239 (17% apart).
        var r = reading(239.0, 1.0, 0.18, 7.5, 2.0, 1.4, 1.3, 0.69);
        assertThat(SanityChecks.check(r)).noneMatch(w -> w.toLowerCase().contains("energy"));
    }

    @Test
    void energyCheckSkipped_whenFatOrCarbohydratesMissing() {
        var r = reading(662.0, 11.0, null, null, null, null, 5.0, null);
        assertThat(SanityChecks.check(r)).noneMatch(w -> w.toLowerCase().contains("energy"));
    }

    // ---- Rule e: nulls never crash ----

    @Test
    void allNulls_noCrash_noWarning() {
        var r = reading(null, null, null, null, null, null, null, null);
        assertThatCode(() -> SanityChecks.check(r)).doesNotThrowAnyException();
        assertThat(SanityChecks.check(r)).isEmpty();
    }

    @Test
    void severalProblems_giveSeveralWarnings() {
        var r = reading(17.0, 2.0, 3.0, 5.0, 9.0, null, 3.3, 150.0);
        List<String> warnings = SanityChecks.check(r);
        assertThat(warnings).hasSizeGreaterThanOrEqualTo(4);
    }
}
