package dev.labellens.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineTest {

    @Test
    void aDropOfMoreThanTheMargin_isARegression() {
        var baseline = Map.of("salt", 90.0, "sugars", 90.0, "fibre", 90.0);
        var current = Map.of("salt", 86.0, "sugars", 87.0, "fibre", 95.0);

        var regressions = Baseline.compare(current, baseline, 3);

        assertThat(regressions).extracting(Baseline.Regression::metric).containsExactly("salt");
        assertThat(regressions.getFirst().drop()).isEqualTo(4.0);
    }

    @Test
    void onlyOverallScoresBlock_perTagScoresAreInformation() {
        var regressions = Baseline.compare(
                Map.of("salt", 50.0, "salt[arabic]", 50.0),
                Map.of("salt", 90.0, "salt[arabic]", 90.0), 3);

        assertThat(regressions).filteredOn(Baseline.Regression::gated)
                .extracting(Baseline.Regression::metric).containsExactly("salt");
    }

    @Test
    void aMetricThatDisappeared_isIgnored() {
        assertThat(Baseline.compare(Map.of(), Map.of("salt[blurry]", 80.0), 3)).isEmpty();
    }

    @Test
    void saveThenLoad_givesTheSameScores(@TempDir Path dir) {
        EvalResult result = new EvalRunner(null).run("test", EvalRunnerTest.cases(),
                EvalRunnerTest.fakeAi(new ArrayList<>()), EvalRunnerTest.SCORER, 2);
        Path file = dir.resolve("baseline.json");

        Baseline.save(file, result);

        assertThat(Baseline.load(file)).get().satisfies(m -> {
            assertThat(m).containsEntry("value", 37.5).containsEntry("value[easy]", 100.0);
            assertThat(Baseline.compare(result.metrics(), m, 3)).isEmpty();
        });
    }

    @Test
    void noBaselineFile_isEmpty(@TempDir Path dir) {
        assertThat(Baseline.load(dir.resolve("missing.json"))).isEmpty();
    }

    @Test
    void theReportIsWritten(@TempDir Path dir) throws Exception {
        EvalResult result = new EvalRunner(null).run("test", EvalRunnerTest.cases(),
                EvalRunnerTest.fakeAi(new ArrayList<>()), EvalRunnerTest.SCORER, 2);

        Path report = Reports.write(dir, result, List.of(), Map.of("model", "fake"));

        String text = Files.readString(report);
        assertThat(text).contains("value", "flaky", "model unavailable", "fake");
        assertThat(dir.resolve(report.getFileName().toString().replace(".txt", ".json"))).exists();
    }

    @Test
    void counters_onlyTheOverallOneBlocks() {
        assertThat(Baseline.gated("caught:all")).isTrue();
        assertThat(Baseline.gated("caught:milk")).isFalse();
        assertThat(Baseline.gated("caught:all[html]")).isFalse();
        assertThat(Baseline.gated("salt")).isTrue();
    }
}
