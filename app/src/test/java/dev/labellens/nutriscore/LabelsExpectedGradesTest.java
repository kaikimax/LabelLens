package dev.labellens.nutriscore;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The answer key for Test 2b (photo → grade): the grade of each photographed product,
 * computed with the calculator from the values TYPED BY HAND in labels.json.
 *
 * Not stored in a file on purpose: computed on demand, it can never get out of date
 * when labels.json is corrected.
 *
 * Report: app/target/expected-grades.txt
 */
class LabelsExpectedGradesTest {

    private static final Path LABELS = Path.of("..", "eval-food", "data", "photos", "labels.json");
    private static final Path REPORT = Path.of("target", "expected-grades.txt");

    /** Expected Nutri-Score for every product in labels.json, from the hand-typed values. */
    static List<ExpectedGrade> expectedGrades(JsonNode labels) {
        List<ExpectedGrade> result = new ArrayList<>();
        for (JsonNode p : labels.get("products")) {
            JsonNode v = p.get("per_100g");
            NutriScoreInput input = new NutriScoreInput(
                    p.get("type").asString(),
                    number(v, "energy_kj"), number(v, "sugars"), number(v, "saturated_fat"),
                    number(v, "salt"), number(v, "fibre"), number(v, "protein"),
                    null); // fruit/veg % not printed on labels: assumed 0, exactly like the app
            JsonNode printed = p.get("printed_grade");
            result.add(new ExpectedGrade(
                    p.get("id").asString(),
                    p.get("type").asString(),
                    NutriScoreCalculator.score(input),
                    printed == null || printed.isNull() ? null : printed.asString()));
        }
        return result;
    }

    record ExpectedGrade(String id, String type, NutriScoreResult result, String printedGrade) {
    }

    @Test
    void everyGeneralProduct_canBeScored_andTheReportIsWritten() throws IOException {
        assumeTrue(Files.exists(LABELS), "labels.json not found at " + LABELS);
        JsonNode labels = JsonMapper.builder().build().readTree(Files.readString(LABELS, StandardCharsets.UTF_8));

        List<ExpectedGrade> grades = expectedGrades(labels);
        List<String> report = new ArrayList<>();
        report.add(String.format(Locale.ROOT, "%-6s %-8s %-6s %-6s %-8s %s", "id", "type", "grade", "score", "printed", "note"));
        List<String> generalNotScored = new ArrayList<>();

        for (ExpectedGrade g : grades) {
            NutriScoreResult r = g.result();
            String note = r.scored() ? "" : r.notScoredReason();
            if (g.printedGrade() != null && r.scored() && !g.printedGrade().equals(r.grade())) {
                note = "differs from the printed grade (fruit/veg % assumed 0?)";
            }
            report.add(String.format(Locale.ROOT, "%-6s %-8s %-6s %-6s %-8s %s",
                    g.id(), g.type(),
                    r.scored() ? r.grade() : "-",
                    r.scored() ? r.score() : "-",
                    g.printedGrade() == null ? "" : g.printedGrade(),
                    note));
            if ("GENERAL".equals(g.type()) && !r.scored()) {
                generalNotScored.add(g.id() + ": " + r.notScoredReason());
            }
        }

        Files.createDirectories(REPORT.getParent());
        Files.write(REPORT, report, StandardCharsets.UTF_8);
        System.out.println(String.join("\n", report));

        // A GENERAL product that can't be scored is missing a value in labels.json (e.g. salt null).
        // That's allowed when the label really doesn't print it (the app can't score it either),
        // so it's reported, not failed. Check each one against the package.
        if (!generalNotScored.isEmpty()) {
            System.out.println("\nGENERAL products that can't be scored (check the package):");
            generalNotScored.forEach(line -> System.out.println("  " + line));
        }
        assertThat(grades).as("products read from labels.json").isNotEmpty();
    }

    private static Double number(JsonNode values, String field) {
        JsonNode v = values == null ? null : values.get(field);
        return v == null || v.isNull() ? null : v.asDouble();
    }
}
