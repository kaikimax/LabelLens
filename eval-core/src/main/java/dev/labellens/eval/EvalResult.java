package dev.labellens.eval;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Everything that happened in one eval, and the numbers computed from it.
 *
 * Counting rule: a run that produced no answer counts as WRONG for every check.
 * Otherwise a model that fails on hard photos would get a better score than one that
 * tries and misreads.
 */
public record EvalResult(String suite, int runsPerCase, Instant startedAt, Instant finishedAt, List<CaseRun> runs) {

    public EvalResult {
        runs = List.copyOf(runs);
    }

    /** "12 / 14 = 85.7%" */
    public record Score(int passed, int total) {
        public double percent() {
            return total == 0 ? Double.NaN : 100.0 * passed / total;
        }
    }

    /** One case where at least one run got one check wrong. */
    public record Disagreement(String caseId, String check, String expected,
                               Map<String, Integer> actualCounts, int wrongRuns, int runs, List<String> notes) {
    }

    // ---- Basic facts --------------------------------------------------------------------

    /** Check names in the order the scorer produced them. */
    public List<String> checkNames() {
        Set<String> names = new LinkedHashSet<>();
        runs.forEach(r -> r.checks().forEach(c -> names.add(c.name())));
        return List.copyOf(names);
    }

    public Set<String> tags() {
        Set<String> tags = new TreeSet<>();
        runs.forEach(r -> tags.addAll(r.tags()));
        return tags;
    }

    public Set<String> caseIds() {
        return runs.stream().map(CaseRun::caseId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

    // ---- Scores -------------------------------------------------------------------------

    /** How many runs produced a usable answer at all. */
    public Score answered() {
        return answered(r -> true);
    }

    public Score answered(String tag) {
        return answered(r -> r.tags().contains(tag));
    }

    private Score answered(Predicate<CaseRun> filter) {
        List<CaseRun> selected = runs.stream().filter(filter).toList();
        int ok = (int) selected.stream().filter(CaseRun::succeeded).count();
        return new Score(ok, selected.size());
    }

    /** Pass rate of one check over all runs (failed runs count as wrong). */
    public Score score(String check) {
        return score(check, r -> true);
    }

    /** Same, only for the cases with this tag. */
    public Score score(String check, String tag) {
        return score(check, r -> r.tags().contains(tag));
    }

    private Score score(String check, Predicate<CaseRun> filter) {
        int passed = 0;
        int total = 0;
        for (CaseRun r : runs) {
            if (!filter.test(r)) {
                continue;
            }
            if (!r.succeeded()) {
                total++; // no answer = wrong
                continue;
            }
            for (Check c : r.checks()) {
                if (c.name().equals(check)) {
                    total++;
                    if (c.passed()) {
                        passed++;
                    }
                }
            }
        }
        return new Score(passed, total);
    }

    /**
     * Flat map of every score, as percentages: "answered", "salt", "salt[arabic]"...
     * This is what the baseline stores and what regressions are computed from.
     */
    public Map<String, Double> metrics() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("answered", answered().percent());
        for (String check : checkNames()) {
            m.put(check, score(check).percent());
        }
        for (String tag : tags()) {
            for (String check : checkNames()) {
                Score s = score(check, tag);
                if (s.total() > 0) {
                    m.put(check + "[" + tag + "]", s.percent());
                }
            }
        }
        return m;
    }

    // ---- What to look at by hand --------------------------------------------------------

    /** Every (case, check) with at least one wrong run, with what the AI answered in each run. */
    public List<Disagreement> disagreements() {
        List<Disagreement> result = new ArrayList<>();
        for (String caseId : caseIds()) {
            List<CaseRun> caseRuns = runsOf(caseId);
            for (String check : checkNames()) {
                List<Check> checks = checksOf(caseRuns, check);
                long wrong = checks.stream().filter(c -> !c.passed()).count();
                if (wrong == 0) {
                    continue;
                }
                Map<String, Integer> counts = new LinkedHashMap<>();
                checks.forEach(c -> counts.merge(c.actual(), 1, Integer::sum));
                List<String> notes = checks.stream()
                        .filter(c -> !c.passed() && c.note() != null)
                        .map(Check::note).distinct().toList();
                result.add(new Disagreement(caseId, check, checks.getFirst().expected(),
                        counts, (int) wrong, checks.size(), notes));
            }
        }
        return result;
    }

    /**
     * (case, check) pairs where the runs gave DIFFERENT answers for the same input.
     * A high number means one run proves nothing: the model is guessing.
     */
    public List<Disagreement> unstable() {
        List<Disagreement> result = new ArrayList<>();
        for (String caseId : caseIds()) {
            List<CaseRun> caseRuns = runsOf(caseId);
            for (String check : checkNames()) {
                List<Check> checks = checksOf(caseRuns, check);
                Map<String, Integer> counts = new LinkedHashMap<>();
                checks.forEach(c -> counts.merge(c.actual(), 1, Integer::sum));
                if (counts.size() > 1) {
                    long wrong = checks.stream().filter(c -> !c.passed()).count();
                    result.add(new Disagreement(caseId, check, checks.getFirst().expected(),
                            counts, (int) wrong, checks.size(), List.of()));
                }
            }
        }
        return result;
    }

    /**
     * Share of (case, check) pairs where every successful run gave the same answer.
     * Counters are left out: one run can hold several checks with the same counter name
     * ("caught:all" once per allergen), so comparing them would compare different allergens,
     * not different runs (bug found on day 6: "49.9% stable" from a 1-run eval).
     */
    public Score stability() {
        int stable = 0;
        int total = 0;
        for (String caseId : caseIds()) {
            List<CaseRun> caseRuns = runsOf(caseId);
            for (String check : checkNames()) {
                if (Check.isCounter(check)) {
                    continue;
                }
                List<Check> checks = checksOf(caseRuns, check);
                if (checks.size() < 2) {
                    continue; // nothing to compare
                }
                total++;
                if (checks.stream().map(Check::actual).distinct().count() == 1) {
                    stable++;
                }
            }
        }
        return new Score(stable, total);
    }

    public List<CaseRun> failedRuns() {
        return runs.stream().filter(r -> !r.succeeded()).toList();
    }

    // ---- Cost ---------------------------------------------------------------------------

    public long totalInputTokens() {
        return runs.stream().mapToLong(CaseRun::inputTokens).sum();
    }

    public long totalOutputTokens() {
        return runs.stream().mapToLong(CaseRun::outputTokens).sum();
    }

    public double averageDurationMs() {
        return runs.stream().mapToLong(CaseRun::durationMs).average().orElse(0);
    }

    public int totalInfraRetries() {
        return runs.stream().mapToInt(CaseRun::infraRetries).sum();
    }

    private List<CaseRun> runsOf(String caseId) {
        return runs.stream().filter(r -> r.caseId().equals(caseId)).toList();
    }

    private static List<Check> checksOf(List<CaseRun> caseRuns, String check) {
        return caseRuns.stream()
                .flatMap(r -> r.checks().stream())
                .filter(c -> c.name().equals(check))
                .toList();
    }
}
