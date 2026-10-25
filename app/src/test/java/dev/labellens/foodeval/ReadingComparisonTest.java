package dev.labellens.foodeval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Day 5, step 2: one test per decision of the comparison rule, written by Oussama.
 *
 * Include the boundaries of every tolerance (just inside → correct, just outside → wrong),
 * the same way NutriScoreCalculatorTest tests the table boundaries.
 *
 * Format reminder:
 *
 *   @Test
 *   void someCase() {
 *       assertThat(new ReadingComparison().sameNumber("salt", 0.5, 0.5)).isTrue();
 *   }
 */
class ReadingComparisonTest {

    @Test
    void salt_identicalValues_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("salt", 0.5, 0.5)).isTrue();
    }

    @Test
    void salt_belowOneGram_oneHundredthOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("salt", 0.5, 0.49)).isTrue();
    }

    @Test
    void salt_fromOneGram_oneTenthOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("salt", 1.0, 1.1)).isTrue();
    }

    @Test
    void salt_fromOneGram_oneHundredth_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("salt", 0.99, 1.0)).isTrue();
    }

    @Test
    void salt_fromOneGram_moreThanOneTenthOff_isWrong() {
        assertThat(new ReadingComparison().sameNumber("salt", 1.5, 1.61)).isFalse();
    }

    @Test
    void energy_exactlyTwoPercentOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("energy_kj", 1000.0, 1020.0)).isTrue();
    }

    @Test
    void energy_justOverTwoPercentOff_isWrong() {
        assertThat(new ReadingComparison().sameNumber("energy_kj", 1000.0, 1020.1)).isFalse();
    }

    @Test
    void fibre_fromTenGrams_oneGramOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("fibre", 10.0, 9.0)).isTrue();
    }

    @Test
    void carbohydrates_fromTenGrams_oneGramOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("carbohydrates", 10.0, 9.0)).isTrue();
    }


    @Test
    void carbohydrates_belowTenGrams_oneTenthOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("carbohydrates", 9.9, 10.0)).isTrue();
    }

    @Test
    void carbohydrates_expectedBelowTen_readingAboveTen_usesTheSmallMargin() {
        assertThat(new ReadingComparison().sameNumber("carbohydrates", 9.9, 10.1)).isFalse();
    }

    @Test
    void fat_fromTenGrams_oneGramOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("fat", 10.0, 9.0)).isTrue();
    }

    @Test
    void saturatedFat_fromTenGrams_oneGramOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("saturated_fat", 10.0, 9.0)).isTrue();
    }

    @Test
    void sugars_fromTenGrams_oneGramOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("sugars", 10.0, 9.0)).isTrue();
    }

    @Test
    void sugars_fromTenGrams_moreThanOneGramOff_isWrong() {
        assertThat(new ReadingComparison().sameNumber("sugars", 10.0, 8.9)).isFalse();
    }

    @Test
    void sugars_belowTenGrams_oneGramOff_isWrong() {
        assertThat(new ReadingComparison().sameNumber("sugars", 9.9, 8.9)).isFalse();
    }

    @Test
    void sugars_belowTenGrams_twoTenthsOff_isWrong() {
        assertThat(new ReadingComparison().sameNumber("sugars", 9.9, 9.7)).isFalse();
    }

    @Test
    void sugars_belowTenGrams_oneTenthOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("sugars", 9.9, 9.8)).isTrue();
    }

    @Test
    void protein_belowTenGrams_oneTenthOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("protein", 4.7, 4.6)).isTrue();
    }

    @Test
    void protein_fromTenGrams_oneGramOff_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("protein", 11.5, 12.5)).isTrue();
    }

    @Test
    void protein_fromTenGrams_moreThanOneGramOff_isWrong() {
        assertThat(new ReadingComparison().sameNumber("protein", 11.5, 12.6)).isFalse();
    }

    @Test
    void unknownFieldName_throws() {
        assertThatThrownBy(() -> new ReadingComparison().sameNumber("protien", 11.5, 11.6))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void notPrinted_andNotRead_isCorrect() {
        assertThat(new ReadingComparison().sameNumber("protein", null, null)).isTrue();
    }

    @Test
    void printed_butNotRead_isWrong() {
        assertThat(new ReadingComparison().sameNumber("protein", 11.5, null)).isFalse();
    }

    @Test
    void notPrinted_butTheAiGaveAValue_isWrong() {
        assertThat(new ReadingComparison().sameNumber("protein", null, 10.0)).isFalse();
    }
}