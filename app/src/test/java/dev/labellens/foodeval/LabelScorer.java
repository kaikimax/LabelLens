package dev.labellens.foodeval;

import dev.labellens.eval.Check;
import dev.labellens.eval.Scorer;
import dev.labellens.nutriscore.NutriScoreCalculator;
import dev.labellens.nutriscore.NutriScoreFromReading;
import dev.labellens.nutriscore.NutriScoreResult;
import dev.labellens.reading.LabelReading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Compares what the AI read with the values typed in labels.json.
 *
 * Test 1 (reading): one check per nutrition value, plus type and ingredients.
 * Test 2b (photo → grade): the AI's values go through the real calculator, and the grade
 * is compared with the grade computed from the typed values. When the grade is wrong,
 * the check names the misread values, so the report shows how errors travel.
 *
 * name and brand are not scored (DATA_PLAN: only there to recognise the product).
 */
public final class LabelScorer implements Scorer<LabelReading, LabelReading> {

    /** labels.json name → how to get the value from a reading. Order = order in the report. */
    static final Map<String, Function<LabelReading, Double>> NUMBERS = new LinkedHashMap<>();

    static {
        NUMBERS.put("energy_kj", LabelReading::energyKj);
        NUMBERS.put("fat", LabelReading::fat);
        NUMBERS.put("saturated_fat", LabelReading::saturatedFat);
        NUMBERS.put("carbohydrates", LabelReading::carbohydrates);
        NUMBERS.put("sugars", LabelReading::sugars);
        NUMBERS.put("fibre", LabelReading::fibre);
        NUMBERS.put("protein", LabelReading::protein);
        NUMBERS.put("salt", LabelReading::salt);
    }

    /** The checks whose mistakes can change a grade (everything the calculator uses). */
    static final Set<String> GRADE_INPUTS = Set.of("type", "energy_kj", "sugars", "saturated_fat", "salt", "fibre", "protein");

    private final FieldComparison compare;

    public LabelScorer(FieldComparison compare) {
        this.compare = compare;
    }

    @Override
    public List<Check> score(LabelReading expected, LabelReading actual) {
        List<Check> checks = new ArrayList<>();

        NUMBERS.forEach((field, value) -> {
            Double e = value.apply(expected);
            Double a = value.apply(actual);
            checks.add(Check.of(field, compare.sameNumber(field, e, a), e, a));
        });
        checks.add(Check.of("type", Objects.equals(expected.type(), actual.type()), expected.type(), actual.type()));
        checks.add(Check.of("ingredients",
                compare.sameText("ingredients", expected.ingredientsText(), actual.ingredientsText()),
                expected.ingredientsText(), actual.ingredientsText()));

        gradeCheck(expected, actual, checks).ifPresent(checks::add);
        return checks;
    }

    /**
     * Only for products whose typed values can be graded: for drinks, oils, cheeses, or a label
     * with a missing value, there's no correct grade to compare with (a wrong type is already
     * caught by the "type" check).
     */
    private static Optional<Check> gradeCheck(LabelReading expected, LabelReading actual, List<Check> checks) {
        NutriScoreResult want = NutriScoreCalculator.score(NutriScoreFromReading.toInput(expected));
        if (!want.scored()) {
            return Optional.empty();
        }
        NutriScoreResult got = NutriScoreCalculator.score(NutriScoreFromReading.toInput(actual));
        boolean same = got.scored() && want.grade().equals(got.grade());
        Check grade = Check.of("grade", same, describe(want), describe(got));
        if (!same) {
            List<String> misread = checks.stream()
                    .filter(c -> !c.passed() && GRADE_INPUTS.contains(c.name()))
                    .map(c -> c.name() + " " + c.expected() + " -> " + c.actual())
                    .toList();
            grade = grade.withNote(misread.isEmpty()
                    ? "no input misread: the values differ only within the comparison tolerance"
                    : "caused by: " + String.join(", ", misread));
        }
        return Optional.of(grade);
    }

    private static String describe(NutriScoreResult r) {
        return r.scored() ? r.grade() + " (" + r.score() + ")" : "not scored: " + r.notScoredReason();
    }
}
