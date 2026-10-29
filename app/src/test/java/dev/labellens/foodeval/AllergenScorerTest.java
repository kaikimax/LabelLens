package dev.labellens.foodeval;

import dev.labellens.eval.Check;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AllergenScorerTest {

    static final AllergenScorer SCORER = new AllergenScorer();

    static List<Check> named(List<Check> checks, String name) {
        return checks.stream().filter(c -> c.name().equals(name)).toList();
    }

    @Test
    void allFound_noFalseAlarm() {
        var checks = SCORER.score(
                new AllergenAnswer(Set.of("en:gluten", "en:milk"), Set.of()),
                new AllergenAnswer(Set.of("en:gluten", "en:milk"), Set.of()));

        assertThat(checks).allMatch(Check::passed);
        assertThat(named(checks, "caught:all")).hasSize(2);
        assertThat(named(checks, "correct-alarm:all")).hasSize(2);
    }

    @Test
    void aMissedAllergen_failsCaught_notCorrectAlarm() {
        var checks = SCORER.score(
                new AllergenAnswer(Set.of("en:gluten", "en:milk"), Set.of()),
                new AllergenAnswer(Set.of("en:gluten"), Set.of()));

        assertThat(named(checks, "caught:milk")).singleElement().satisfies(c -> {
            assertThat(c.passed()).isFalse();
            assertThat(c.actual()).isEqualTo("missed");
        });
        assertThat(named(checks, "correct-alarm:all")).allMatch(Check::passed);
        assertThat(named(checks, "allergens").getFirst().passed()).isFalse();
    }

    @Test
    void aFalseAlarm_failsCorrectAlarm_notCaught() {
        var checks = SCORER.score(
                new AllergenAnswer(Set.of("en:gluten"), Set.of()),
                new AllergenAnswer(Set.of("en:gluten", "en:nuts"), Set.of()));

        assertThat(named(checks, "caught:all")).allMatch(Check::passed);
        assertThat(named(checks, "correct-alarm:nuts")).singleElement()
                .satisfies(c -> assertThat(c.passed()).isFalse());
    }

    @Test
    void noAllergenAndNoneFound_isCorrect_withNoCounters() {
        var checks = SCORER.score(new AllergenAnswer(Set.of(), Set.of()), new AllergenAnswer(Set.of(), Set.of()));

        assertThat(named(checks, "allergens").getFirst().passed()).isTrue();
        assertThat(named(checks, "caught:all")).isEmpty();
    }

    @Test
    void traces_areScoredSeparately() {
        var checks = SCORER.score(
                new AllergenAnswer(Set.of("en:milk"), Set.of("en:nuts")),
                new AllergenAnswer(Set.of("en:milk"), Set.of()));

        assertThat(named(checks, "allergens").getFirst().passed()).isTrue();
        assertThat(named(checks, "traces").getFirst().passed()).isFalse();
        assertThat(named(checks, "traces-caught:all")).singleElement()
                .satisfies(c -> assertThat(c.passed()).isFalse());
    }

    @Test
    void tracesNotComputed_areSkipped_notCountedWrong() {
        var checks = SCORER.score(
                new AllergenAnswer(Set.of("en:milk"), Set.of("en:nuts")),
                new AllergenAnswer(Set.of("en:milk"), null));

        assertThat(checks).noneMatch(c -> c.name().startsWith("traces"));
    }

    @Test
    void shortNames() {
        assertThat(AllergenScorer.shortName("en:sulphur-dioxide-and-sulphites")).isEqualTo("sulphites");
        assertThat(AllergenScorer.shortName("en:milk")).isEqualTo("milk");
    }
}
