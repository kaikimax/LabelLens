package dev.labellens.nutriscore;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The specification for NutriScoreCalculator, written as tests.
 * Remove @Disabled, then implement the calculator until everything is green:
 *
 *     mvn -pl app test -Dtest=NutriScoreCalculatorTest
 *
 * The 5 "real product" cases come from Open Food Facts product pages, which show
 * every input and every point of the official computation (links in each test).
 * They are the reference: if your code disagrees, your code is wrong.
 */
@Disabled("Implement NutriScoreCalculator.score(), then remove this line")
class NutriScoreCalculatorTest {

    private static NutriScoreInput general(Double energyKj, Double sugars, Double saturatedFat, Double salt,
                                           Double fibre, Double protein, Double fruitVeg) {
        return new NutriScoreInput("GENERAL", energyKj, sugars, saturatedFat, salt, fibre, protein, fruitVeg);
    }

    // ---- Real products: grade A to E ----------------------------------------------------

    @Test
    void fromageBlanc_gradeA_proteinCounted() {
        // https://world.openfoodfacts.org/product/3250390010098
        var r = NutriScoreCalculator.score(general(207.0, 4.7, 0.1, 0.1, 0.0, 7.5, 0.0));

        assertThat(r.scored()).isTrue();
        assertThat(r.points()).isEqualTo(new NutriScorePoints(0, 1, 0, 0, 3, 0, 0, true));
        assertThat(r.score()).isEqualTo(-2);
        assertThat(r.grade()).isEqualTo("A");
    }

    @Test
    void oatFlakes_gradeA_highFibre() {
        // https://world.openfoodfacts.org/product/3168930003632
        var r = NutriScoreCalculator.score(general(1570.0, 1.1, 1.5, 0.01, 9.0, 11.0, 0.0));

        assertThat(r.points()).isEqualTo(new NutriScorePoints(4, 0, 1, 0, 4, 5, 0, true));
        assertThat(r.score()).isEqualTo(-4);
        assertThat(r.grade()).isEqualTo("A");
    }

    @Test
    void strawberryJam_gradeD_proteinNotCounted() {
        // https://world.openfoodfacts.org/product/3560070341832
        // Protein isn't shown on the page because it doesn't count; 0.4 g is a typical jam value.
        var r = NutriScoreCalculator.score(general(1040.0, 59.0, 0.0, 0.0, 0.7, 0.4, 45.0));

        assertThat(r.points().energy()).isEqualTo(3);
        assertThat(r.points().sugars()).isEqualTo(15);
        assertThat(r.points().saturatedFat()).isZero();
        assertThat(r.points().salt()).isZero();
        assertThat(r.points().fibre()).isZero();
        assertThat(r.points().fruitVegLegumes()).isEqualTo(1);
        assertThat(r.points().proteinCounted()).isFalse(); // negative points = 18, not below 11
        assertThat(r.score()).isEqualTo(17);
        assertThat(r.grade()).isEqualTo("D");
    }

    @Test
    void anotherStrawberryJam_gradeD() {
        // https://world.openfoodfacts.org/product/3368958362203
        var r = NutriScoreCalculator.score(general(940.0, 54.0, 0.1, 0.02, 1.2, 0.5, 55.0));

        assertThat(r.points().energy()).isEqualTo(2);
        assertThat(r.points().sugars()).isEqualTo(15);
        assertThat(r.points().fruitVegLegumes()).isEqualTo(1);
        assertThat(r.points().proteinCounted()).isFalse();
        assertThat(r.score()).isEqualTo(16);
        assertThat(r.grade()).isEqualTo("D");
    }

    @Test
    void chocolateBiscuits_gradeE() {
        // https://world.openfoodfacts.org/product/4001518007662
        // Protein isn't shown on the page because it doesn't count; 6.0 g is a typical biscuit value.
        var r = NutriScoreCalculator.score(general(2196.0, 41.0, 19.0, 0.33, 2.2, 6.0, 0.0));

        assertThat(r.points().energy()).isEqualTo(6);
        assertThat(r.points().sugars()).isEqualTo(11);
        assertThat(r.points().saturatedFat()).isEqualTo(10);
        assertThat(r.points().salt()).isEqualTo(1);
        assertThat(r.points().fibre()).isZero();
        assertThat(r.points().proteinCounted()).isFalse();
        assertThat(r.score()).isEqualTo(28);
        assertThat(r.grade()).isEqualTo("E");
    }

    // ---- The protein rule ---------------------------------------------------------------

    @Test
    void protein_doesNotChangeTheScore_whenNotCounted() {
        var lowProtein = NutriScoreCalculator.score(general(2196.0, 41.0, 19.0, 0.33, 2.2, 0.0, 0.0));
        var highProtein = NutriScoreCalculator.score(general(2196.0, 41.0, 19.0, 0.33, 2.2, 30.0, 0.0));

        assertThat(highProtein.score()).isEqualTo(lowProtein.score());
    }

    // ---- What is NOT scored ---------------------------------------------------------------

    @Test
    void drinksCheeseAndOils_areNotScored() {
        for (String type : new String[]{"DRINK", "CHEESE", "OIL"}) {
            var r = NutriScoreCalculator.score(
                    new NutriScoreInput(type, 1000.0, 10.0, 5.0, 1.0, 1.0, 5.0, 0.0));

            assertThat(r.scored()).as(type).isFalse();
            assertThat(r.notScoredReason()).as(type).isNotBlank();
            assertThat(r.grade()).as(type).isNull();
        }
    }

    @Test
    void missingSalt_isNotScored_andTheReasonSaysWhy() {
        var r = NutriScoreCalculator.score(general(1000.0, 10.0, 5.0, null, 1.0, 5.0, 0.0));

        assertThat(r.scored()).isFalse();
        assertThat(r.notScoredReason().toLowerCase()).contains("salt");
    }

    @Test
    void missingFibreProteinAndFruitVeg_countAsZero() {
        var withZeros = NutriScoreCalculator.score(general(207.0, 4.7, 0.1, 0.1, 0.0, 0.0, 0.0));
        var withNulls = NutriScoreCalculator.score(general(207.0, 4.7, 0.1, 0.1, null, null, null));

        assertThat(withNulls.scored()).isTrue();
        assertThat(withNulls.score()).isEqualTo(withZeros.score());
        assertThat(withNulls.grade()).isEqualTo(withZeros.grade());
    }

    // ---- TODO (Oussama): boundary tests ------------------------------------------------
    // Once you have the official tables, add tests for values exactly ON a threshold and
    // just above it, for at least salt, sugars and energy. Example shape:
    //
    //   @Test
    //   void salt_exactlyOnThreshold_vs_justAbove() { ... }
    //
    // These catch the most common bug in this kind of code: ">" written instead of ">=".
    // Check in the specification which one each table uses.
}
