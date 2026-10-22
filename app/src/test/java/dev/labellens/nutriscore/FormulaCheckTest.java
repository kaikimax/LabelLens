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
 * Test 2a: the calculator against 5,000 real products scored by Open Food Facts.
 *
 * Data: eval-food/data/off/formula_check.jsonl, built by training/build_formula_check.py.
 * Report: app/target/formula-check-report.txt (every mismatch, with its inputs).
 *
 * A mismatch is not automatically a bug. It's one of three things:
 *   1. a bug in the calculator,
 *   2. a data problem in Open Food Facts (fake zeros, wrong category...),
 *   3. a rule the calculator doesn't implement (red meat, a category that slipped through).
 * Investigate at least 10 by hand and write the conclusions in docs/notes/day4-formula-check.md.
 */
class FormulaCheckTest {

    private static final Path DATA = Path.of("..", "eval-food", "data", "off", "formula_check.jsonl");
    private static final Path REPORT = Path.of("target", "formula-check-report.txt");
    private static final double REQUIRED_AGREEMENT = 0.99;

    /**
     * Open Food Facts stores the fruit/vegetable percentage under different names depending
     * on the product. First one found wins; none found = 0.
     */
    private static final List<String> FRUIT_VEG_FIELDS = List.of(
            "fruits-vegetables-legumes",
            "fruits-vegetables-legumes-estimate-from-ingredients",
            "fruits-vegetables-nuts",
            "fruits-vegetables-nuts-estimate-from-ingredients");

    @Test
    void calculatorAgreesWithOpenFoodFacts() throws IOException {
        assumeTrue(Files.exists(DATA), "Run training/build_formula_check.py first to create " + DATA);

        JsonMapper json = JsonMapper.builder().build();
        List<String> fruitVegMismatches = new ArrayList<>();
        List<String> unexplainedMismatches = new ArrayList<>();
        int total = 0;
        int sameGrade = 0;
        int sameScore = 0;

        for (String line : Files.readAllLines(DATA, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode p = json.readTree(line);
            NutriScoreInput input = new NutriScoreInput("GENERAL",
                    number(p, "energy_kj"), number(p, "sugars"), number(p, "saturated_fat"),
                    number(p, "salt"), number(p, "fibre"), number(p, "protein"), fruitVeg(p));

            NutriScoreResult result = NutriScoreCalculator.score(input);
            String expectedGrade = p.get("expected_grade").asString();
            int expectedScore = p.get("expected_score").asInt();
            total++;

            boolean gradeOk = result.scored() && expectedGrade.equals(result.grade());
            boolean scoreOk = result.scored() && result.score() == expectedScore;
            if (gradeOk) {
                sameGrade++;
            }
            if (scoreOk) {
                sameScore++;
            }
            if (!gradeOk) {
                String mismatch = String.format(Locale.ROOT,
                        "%s  expected %s (%d)  got %s (%s)  inputs=%s  points=%s",
                        p.get("code").asString(), expectedGrade, expectedScore,
                        result.grade(), result.score(), input, result.points());
                if (explainedByFruitVeg(input, expectedScore)) {
                    fruitVegMismatches.add(mismatch);
                } else {
                    unexplainedMismatches.add(mismatch);
                }
            }
        }

        double strictAgreement = (double) sameGrade / total;
        double formulaAgreement = (double) (total - unexplainedMismatches.size()) / total;

        List<String> report = new ArrayList<>();
        report.add(String.format(Locale.ROOT, "Products: %d", total));
        report.add(String.format(Locale.ROOT, "Same grade (strict): %d (%.2f%%)", sameGrade, strictAgreement * 100));
        report.add(String.format(Locale.ROOT, "Same score (strict): %d (%.2f%%)", sameScore, 100.0 * sameScore / total));
        report.add(String.format(Locale.ROOT,
                "Mismatches explained by the fruit/vegetable input: %d", fruitVegMismatches.size()));
        report.add(String.format(Locale.ROOT,
                "Agreement once fruit/vegetable data is set aside: %.2f%%  (checked against %.0f%%)",
                formulaAgreement * 100, REQUIRED_AGREEMENT * 100));
        report.add("");
        report.add("UNEXPLAINED mismatches (investigate these): " + unexplainedMismatches.size());
        report.addAll(unexplainedMismatches);
        report.add("");
        report.add("Mismatches explained by the fruit/vegetable input: " + fruitVegMismatches.size());
        report.addAll(fruitVegMismatches);
        Files.createDirectories(REPORT.getParent());
        Files.write(REPORT, report, StandardCharsets.UTF_8);

        System.out.println(String.join("\n", report.subList(0, Math.min(report.size(), 25))));
        System.out.println("Full report: " + REPORT.toAbsolutePath());

        assertThat(formulaAgreement)
                .as("Agreement with Open Food Facts once fruit/vegetable data is set aside (see " + REPORT + ")")
                .isGreaterThanOrEqualTo(REQUIRED_AGREEMENT);
    }

    /**
     * The fruit/vegetable % in the export often differs from the one OFF used to compute its
     * grade (missing, or a different estimate). This retries the calculator with each possible
     * fruit/vegetable point value (0, 1, 2 and 5 points, using one % from each band):
     * if one of them gives exactly OFF's score, the mismatch comes from that input, not the formula.
     */
    private static boolean explainedByFruitVeg(NutriScoreInput input, int expectedScore) {
        for (double percent : new double[]{0, 50, 70, 90}) {
            NutriScoreInput retry = new NutriScoreInput(input.type(), input.energyKj(), input.sugars(),
                    input.saturatedFat(), input.salt(), input.fibre(), input.protein(), percent);
            NutriScoreResult result = NutriScoreCalculator.score(retry);
            if (result.scored() && result.score() == expectedScore) {
                return true;
            }
        }
        return false;
    }

    private static Double number(JsonNode p, String field) {
        JsonNode value = p.get(field);
        return value == null || value.isNull() ? null : value.asDouble();
    }

    private static Double fruitVeg(JsonNode p) {
        JsonNode all = p.get("fruit_veg");
        if (all != null) {
            for (String field : FRUIT_VEG_FIELDS) {
                JsonNode value = all.get(field);
                if (value != null && !value.isNull()) {
                    return value.asDouble();
                }
            }
        }
        return 0.0;
    }
}