package dev.labellens.foodeval;

import dev.labellens.eval.Check;
import dev.labellens.eval.Scorer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Test 3: compares the allergens found with the answer key.
 *
 * Two numbers matter, and they're NOT the same mistake (SPEC 8.2):
 *   caught         = of the allergens really there, how many were found (recall).
 *                    A miss is the DANGEROUS mistake: this is the main score.
 *   correct-alarm  = of the allergens reported, how many are really there (precision).
 *                    A false alarm is annoying, not dangerous.
 *
 * Checks:
 *   "allergens", "traces"   the whole set right or wrong, per product (listed in the report)
 *   "caught:all", "caught:milk"...                 counters (see Check.isCounter)
 *   "correct-alarm:all", "correct-alarm:milk"...  counters
 *   "traces-caught:all", "traces-correct:all"      counters for "may contain"
 */
public final class AllergenScorer implements Scorer<AllergenAnswer, AllergenAnswer> {

    @Override
    public List<Check> score(AllergenAnswer expected, AllergenAnswer actual) {
        List<Check> checks = new ArrayList<>();
        checks.add(Check.of("allergens", expected.allergens().equals(actual.allergens()),
                expected.allergens(), actual.allergens()));

        for (String tag : expected.allergens()) {
            boolean found = actual.allergens().contains(tag);
            checks.add(Check.of("caught:all", found, tag, found ? "found" : "missed"));
            checks.add(Check.of("caught:" + shortName(tag), found, tag, found ? "found" : "missed"));
        }
        for (String tag : actual.allergens()) {
            boolean real = expected.allergens().contains(tag);
            checks.add(Check.of("correct-alarm:all", real, real ? tag : "none", tag));
            checks.add(Check.of("correct-alarm:" + shortName(tag), real, real ? tag : "none", tag));
        }

        if (actual.mayContain() != null) {
            Set<String> wantTraces = new TreeSet<>(expected.mayContain());
            checks.add(Check.of("traces", wantTraces.equals(actual.mayContain()), wantTraces, actual.mayContain()));
            for (String tag : wantTraces) {
                checks.add(Check.of("traces-caught:all", actual.mayContain().contains(tag), tag, actual.mayContain()));
            }
            for (String tag : actual.mayContain()) {
                checks.add(Check.of("traces-correct:all", wantTraces.contains(tag), wantTraces, tag));
            }
        }
        return checks;
    }

    /** "en:sulphur-dioxide-and-sulphites" → "sulphites": short names keep the report readable. */
    static String shortName(String tag) {
        return switch (tag) {
            case "en:sulphur-dioxide-and-sulphites" -> "sulphites";
            case "en:sesame-seeds" -> "sesame";
            default -> tag.startsWith("en:") ? tag.substring(3) : tag;
        };
    }
}
