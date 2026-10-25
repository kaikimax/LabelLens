package dev.labellens.foodeval;

import dev.labellens.eval.Check;
import dev.labellens.reading.LabelReading;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scorer, with a stand-in comparison rule (exact match), so these tests don't depend
 * on the real rule from step 2. Part of the normal build: no model, no cost.
 */
class LabelScorerTest {

    static final FieldComparison EXACT = new FieldComparison() {
        @Override
        public boolean sameNumber(String field, Double expected, Double actual) {
            return Objects.equals(expected, actual);
        }

        @Override
        public boolean sameText(String field, String expected, String actual) {
            return Objects.equals(expected, actual);
        }
    };

    static final LabelScorer SCORER = new LabelScorer(EXACT);

    /** Product p004, the strawberry jam: D (18) from the typed values. */
    static LabelReading jam(String type, Double salt, Double fat) {
        return new LabelReading("Fantaisie", "Al Baraka", type, 1088.4, fat, 0.0, 62.9, 60.0, null, 0.47, salt,
                "Fraises, Sucre", "fr", "100g");
    }

    static Check check(List<Check> checks, String name) {
        return checks.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void aPerfectReading_passesEveryCheck_includingTheGrade() {
        List<Check> checks = SCORER.score(jam("GENERAL", 0.05, 0.3), jam("GENERAL", 0.05, 0.3));

        assertThat(checks).extracting(Check::name).containsExactly(
                "energy_kj", "fat", "saturated_fat", "carbohydrates", "sugars", "fibre", "protein", "salt",
                "type", "ingredients", "grade");
        assertThat(checks).allMatch(Check::passed);
        assertThat(check(checks, "grade").expected()).isEqualTo("D (18)");
    }

    @Test
    void aMisreadThatChangesTheGrade_isNamedInTheGradeCheck() {
        // Salt 2.05 instead of 0.05: +10 salt points → 28 → E.
        List<Check> checks = SCORER.score(jam("GENERAL", 0.05, 0.3), jam("GENERAL", 2.05, 0.3));

        Check grade = check(checks, "grade");
        assertThat(grade.passed()).isFalse();
        assertThat(grade.actual()).isEqualTo("E (28)");
        assertThat(grade.note()).isEqualTo("caused by: salt 0.05 -> 2.05");
    }

    @Test
    void aMisreadThatDoesntChangeTheGrade_onlyFailsItsOwnCheck() {
        // Fat isn't used by Nutri-Score.
        List<Check> checks = SCORER.score(jam("GENERAL", 0.05, 0.3), jam("GENERAL", 0.05, 3.0));

        assertThat(check(checks, "fat").passed()).isFalse();
        assertThat(check(checks, "grade").passed()).isTrue();
    }

    @Test
    void aWrongType_failsTheTypeCheck_andTheGrade() {
        List<Check> checks = SCORER.score(jam("GENERAL", 0.05, 0.3), jam("DRINK", 0.05, 0.3));

        assertThat(check(checks, "type").passed()).isFalse();
        Check grade = check(checks, "grade");
        assertThat(grade.passed()).isFalse();
        assertThat(grade.note()).contains("type GENERAL -> DRINK");
    }

    @Test
    void aProductThatCantBeGraded_hasNoGradeCheck() {
        // An oil: no correct grade to compare with in v1.
        List<Check> checks = SCORER.score(jam("OIL", 0.05, 0.3), jam("OIL", 0.05, 0.3));

        assertThat(checks).extracting(Check::name).doesNotContain("grade");
    }

    @Test
    void notPrinted_andNotRead_isPassedToTheRuleAsTwoNulls() {
        List<Check> checks = SCORER.score(jam("GENERAL", 0.05, 0.3), jam("GENERAL", 0.05, 0.3));

        Check fibre = check(checks, "fibre");
        assertThat(fibre.expected()).isEqualTo("null");
        assertThat(fibre.passed()).isTrue(); // with the exact stand-in rule; the real rule decides for itself
    }
}
