package dev.labellens.eval;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns an {@link EvalResult} into two files:
 * - a text report for humans (scores, then every disagreement to review by hand);
 * - a JSON file with every run and check, for later analysis.
 */
public final class Reports {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneOffset.UTC);

    private Reports() {
    }

    /**
     * Writes {suite}-{date}.txt and .json into the folder. Returns the text report's path.
     *
     * @param info extra lines for the header, e.g. "model" → "gpt-4.1-mini", "estimated cost" → "$0.03"
     */
    public static Path write(Path folder, EvalResult result, List<Baseline.Regression> regressions,
                             Map<String, String> info) {
        String base = result.suite() + "-" + FILE_TIME.format(result.startedAt());
        Path text = folder.resolve(base + ".txt");
        Path json = folder.resolve(base + ".json");
        try {
            Files.createDirectories(folder);
            Files.writeString(text, text(result, regressions, info), StandardCharsets.UTF_8);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("suite", result.suite());
            details.put("startedAt", result.startedAt().toString());
            details.put("runsPerCase", result.runsPerCase());
            details.put("info", info);
            details.put("metrics", result.metrics());
            details.put("runs", result.runs());
            Files.writeString(json, JSON.writer().withDefaultPrettyPrinter().writeValueAsString(details), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the report to " + folder, e);
        }
        return text;
    }

    public static String text(EvalResult r, List<Baseline.Regression> regressions, Map<String, String> info) {
        List<String> out = new ArrayList<>();
        out.add("EVAL REPORT: " + r.suite());
        out.add("=".repeat(80));
        out.add(String.format(Locale.ROOT, "Started:        %s (took %d min %d s)", r.startedAt(),
                r.duration().toMinutes(), r.duration().toSecondsPart()));
        out.add(String.format(Locale.ROOT, "Cases:          %d, %d run(s) each", r.caseIds().size(), r.runsPerCase()));
        if (info != null) {
            info.forEach((k, v) -> out.add(String.format(Locale.ROOT, "%-15s %s", k + ":", v)));
        }
        out.add("");
        out.add("Counting rule: a run with no usable answer counts as WRONG for every check.");
        out.add("");

        // ---- Overall scores
        out.add("SCORES");
        out.add("-".repeat(80));
        out.add(line("answered", r.answered()));
        for (String check : r.checkNames()) {
            out.add(line(check, r.score(check)));
        }
        EvalResult.Score stability = r.stability();
        out.add("");
        out.add(stability.total() == 0
                ? "Stability: not measured (needs 2 or more runs per case)"
                : String.format(Locale.ROOT, "Stability: %d / %d (case, check) pairs gave the same answer in every run (%s)",
                        stability.passed(), stability.total(), percent(stability)));

        // ---- By tag
        Set<String> tags = r.tags();
        if (!tags.isEmpty()) {
            out.add("");
            out.add("BY SITUATION (percent; number of cases in brackets; few cases = unreliable)");
            out.add("-".repeat(80));
            for (String tag : tags) {
                long cases = r.runs().stream().filter(x -> x.tags().contains(tag)).map(CaseRun::caseId).distinct().count();
                String scores = r.checkNames().stream()
                        .map(c -> c + " " + percent(r.score(c, tag)))
                        .collect(Collectors.joining(" | "));
                out.add(String.format(Locale.ROOT, "%-22s %s", tag + " (" + cases + ")", scores));
            }
        }

        // ---- Baseline
        out.add("");
        out.add("COMPARED WITH THE BASELINE");
        out.add("-".repeat(80));
        if (regressions == null) {
            out.add("No baseline yet.");
        } else if (regressions.isEmpty()) {
            out.add("No score dropped beyond the allowed margin.");
        } else {
            for (Baseline.Regression g : regressions) {
                out.add(String.format(Locale.ROOT, "%s %-30s %.1f%% -> %.1f%% (-%.1f points)",
                        g.gated() ? "BLOCKS" : "info  ", g.metric(), g.baseline(), g.current(), g.drop()));
            }
        }

        // ---- What to review by hand
        out.add("");
        out.add("DISAGREEMENTS (review each one: AI error, answer-key error, or comparison rule?)");
        out.add("-".repeat(80));
        String currentCase = null;
        for (EvalResult.Disagreement d : r.disagreements()) {
            if (Check.isCounter(d.check())) {
                continue; // counters ("caught:milk") repeat what the product-level checks already show
            }
            if (!d.caseId().equals(currentCase)) {
                currentCase = d.caseId();
                out.add(currentCase);
            }
            out.add(String.format(Locale.ROOT, "  %-14s expected %s | got %s | wrong in %d/%d runs",
                    d.check(), d.expected(), counts(d.actualCounts()), d.wrongRuns(), d.runs()));
            d.notes().forEach(n -> out.add("                 " + n));
        }
        if (currentCase == null) {
            out.add("None.");
        }

        List<CaseRun> failed = r.failedRuns();
        out.add("");
        out.add("RUNS WITH NO USABLE ANSWER");
        out.add("-".repeat(80));
        failed.forEach(f -> out.add("  " + f.caseId() + " run " + f.run() + ": " + f.failure()));
        if (failed.isEmpty()) {
            out.add("None.");
        }

        List<EvalResult.Disagreement> unstable = r.unstable().stream()
                .filter(u -> !Check.isCounter(u.check())).toList();
        out.add("");
        out.add("UNSTABLE (same input, different answers between runs)");
        out.add("-".repeat(80));
        unstable.forEach(u -> out.add(String.format(Locale.ROOT, "  %-6s %-14s expected %s | got %s",
                u.caseId(), u.check(), u.expected(), counts(u.actualCounts()))));
        if (unstable.isEmpty()) {
            out.add("None.");
        }

        out.add("");
        out.add("COST");
        out.add("-".repeat(80));
        out.add(String.format(Locale.ROOT, "Tokens: %,d in, %,d out | average %.1f s per run | %d infrastructure retries",
                r.totalInputTokens(), r.totalOutputTokens(), r.averageDurationMs() / 1000, r.totalInfraRetries()));

        return String.join(System.lineSeparator(), out) + System.lineSeparator();
    }

    /** Below this many examples, a percentage is noise: it's shown, but marked. */
    static final int TOO_FEW = 10;

    private static String line(String name, EvalResult.Score s) {
        String few = s.total() > 0 && s.total() < TOO_FEW ? "   (too few to measure)" : "";
        return String.format(Locale.ROOT, "%-24s %4d / %-4d %s%s", name, s.passed(), s.total(), percent(s), few);
    }

    private static String percent(EvalResult.Score s) {
        return s.total() == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", s.percent());
    }

    /** {"0.5": 2, "1.5": 1} → "0.5 (x2), 1.5 (x1)" */
    private static String counts(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(e -> e.getKey() + " (x" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
    }
}
