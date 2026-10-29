package dev.labellens.eval;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The harness, tested with a fake "AI": it doubles a number, sometimes wrongly, sometimes not at all.
 * No food, no Spring, no network: if these pass, the counting is right whatever the AI step is.
 */
class EvalRunnerTest {

    /** The "AI" answers input × 2, except: case "bad" answers 0, case "flaky" alternates, case "down" fails. */
    static Subject<Integer, Integer> fakeAi(List<String> calls) {
        int[] flakyCalls = {0};
        return input -> {
            calls.add(String.valueOf(input));
            return switch (input) {
                case 7 -> Attempt.answered(0, 10, 100, 5, 0);                               // "bad"
                case 9 -> Attempt.answered(flakyCalls[0]++ % 2 == 0 ? 18 : 19, 10, 100, 5, 0); // "flaky"
                case 11 -> Attempt.failed("model unavailable", 10, 0, 0, 1);               // "down"
                default -> Attempt.answered(input * 2, 10, 100, 5, 0);
            };
        };
    }

    static final Scorer<Integer, Integer> SCORER = (expected, answer) -> List.of(
            Check.of("value", expected.equals(answer), expected, answer),
            Check.of("even", answer % 2 == 0, true, answer % 2 == 0));

    static List<EvalCase<Integer, Integer>> cases() {
        return List.of(
                new EvalCase<>("good", 3, 6, Set.of("easy")),
                new EvalCase<>("bad", 7, 14, Set.of("hard")),
                new EvalCase<>("flaky", 9, 18, Set.of("hard")),
                new EvalCase<>("down", 11, 22, Set.of("hard")));
    }

    EvalResult run(int runsPerCase) {
        return new EvalRunner(null).run("test", cases(), fakeAi(new ArrayList<>()), SCORER, runsPerCase);
    }

    @Test
    void everyCaseRunsTheRequestedNumberOfTimes() {
        List<String> calls = new ArrayList<>();
        EvalResult r = new EvalRunner(null).run("test", cases(), fakeAi(calls), SCORER, 3);

        assertThat(calls).hasSize(12);
        assertThat(r.runs()).hasSize(12);
        assertThat(r.caseIds()).containsExactly("good", "bad", "flaky", "down");
    }

    @Test
    void aRunWithNoAnswer_countsAsWrongForEveryCheck() {
        EvalResult r = run(2);

        // 8 runs: "down" fails twice → 6 answered.
        assertThat(r.answered()).isEqualTo(new EvalResult.Score(6, 8));
        // value: good ×2, flaky ×1 right; bad ×2, flaky ×1 wrong; down ×2 wrong (no answer).
        assertThat(r.score("value")).isEqualTo(new EvalResult.Score(3, 8));
        assertThat(r.failedRuns()).extracting(CaseRun::caseId).containsOnly("down");
    }

    @Test
    void scoresAreAlsoSplitByTag() {
        EvalResult r = run(2);

        assertThat(r.score("value", "easy")).isEqualTo(new EvalResult.Score(2, 2));
        assertThat(r.score("value", "hard")).isEqualTo(new EvalResult.Score(1, 6));
        assertThat(r.metrics())
                .containsEntry("value", 37.5)
                .containsEntry("value[easy]", 100.0)
                .containsKey("even[hard]");
    }

    @Test
    void disagreementsShowWhatTheAiAnsweredInEachRun() {
        EvalResult r = run(2);

        var flaky = r.disagreements().stream()
                .filter(d -> d.caseId().equals("flaky") && d.check().equals("value"))
                .findFirst().orElseThrow();
        assertThat(flaky.expected()).isEqualTo("18");
        assertThat(flaky.actualCounts()).isEqualTo(Map.of("18", 1, "19", 1));
        assertThat(flaky.wrongRuns()).isEqualTo(1);

        assertThat(r.disagreements()).noneMatch(d -> d.caseId().equals("good"));
    }

    @Test
    void stability_detectsTheSameInputGivingDifferentAnswers() {
        EvalResult r = run(2);

        assertThat(r.unstable()).extracting(EvalResult.Disagreement::caseId).containsOnly("flaky");
        // good, bad, flaky × 2 checks = 6 pairs; flaky's 2 checks both changed.
        assertThat(r.stability()).isEqualTo(new EvalResult.Score(4, 6));
    }

    @Test
    void stability_ignoresCounters_soOneRunMeasuresNothing() {
        // Two checks with the same counter name in ONE run (like "caught:all" once per allergen).
        Scorer<Integer, Integer> counters = (expected, answer) -> List.of(
                Check.of("caught:all", true, "a", "a"),
                Check.of("caught:all", false, "b", "x"));
        EvalResult r = new EvalRunner(null).run("test", cases(), fakeAi(new ArrayList<>()), counters, 1);

        assertThat(r.stability().total()).isZero();
    }

    @Test
    void aSubjectThatThrows_isRecordedAsAFailedRun() {
        Subject<Integer, Integer> crashing = input -> {
            throw new IllegalStateException("boom");
        };
        EvalResult r = new EvalRunner(null).run("test", cases(), crashing, SCORER, 1);

        assertThat(r.answered()).isEqualTo(new EvalResult.Score(0, 4));
        assertThat(r.failedRuns().getFirst().failure()).contains("boom");
    }

    @Test
    void costIsAddedUp() {
        EvalResult r = run(1);

        assertThat(r.totalInputTokens()).isEqualTo(300);
        assertThat(r.totalInfraRetries()).isEqualTo(1);
    }
}
