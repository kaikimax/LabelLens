package dev.labellens.foodeval;

import dev.labellens.TestcontainersConfiguration;
import dev.labellens.ai.AiCallRecord;
import dev.labellens.ai.ImageInput;
import dev.labellens.eval.Attempt;
import dev.labellens.eval.Baseline;
import dev.labellens.eval.EvalResult;
import dev.labellens.eval.EvalRunner;
import dev.labellens.eval.Reports;
import dev.labellens.eval.Subject;
import dev.labellens.reading.LabelReader;
import dev.labellens.reading.LabelReading;
import dev.labellens.reading.ReadOutcome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Test 1 (reading) and Test 2b (photo → grade) against the REAL model.
 *
 * Costs money, so it never runs in the normal build: only with  mvn -pl app test -Peval
 * (options in app/pom.xml, profile "eval"). Needs the Azure key in .env and Docker running.
 *
 * Output: eval-food/reports/reading-{date}.txt (+ .json with every run).
 * Fails if an overall score dropped more than eval.maxDrop points below eval-food/baseline.json.
 */
@Tag("eval")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, ReadingEvalTest.RecordCalls.class})
class ReadingEvalTest {

    private static final Path EVAL_FOOD = Path.of("..", "eval-food");
    private static final Path LABELS = EVAL_FOOD.resolve("data/photos/labels.json");
    private static final Path IMAGES = EVAL_FOOD.resolve("data/photos/images");
    private static final Path REPORTS = EVAL_FOOD.resolve("reports");
    private static final Path BASELINE = EVAL_FOOD.resolve("baseline.json");

    /** List price of gpt-4.1-mini in US$ per million tokens. Check the Azure pricing page if it changes. */
    private static final double PRICE_IN = 0.40;
    private static final double PRICE_OUT = 1.60;

    /** A rate-limited call is retried after a pause; it says nothing about the model's reading. */
    private static final int MAX_INFRA_RETRIES = 3;
    private static final long INFRA_PAUSE_MS = 30_000;

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordCalls {
        @Bean
        @Primary
        RecordingAiCallLog recordingAiCallLog() {
            return new RecordingAiCallLog();
        }
    }

    @Autowired
    LabelReader reader;

    @Autowired
    RecordingAiCallLog calls;

    @Autowired
    Environment env;

    @Test
    void readingAndGrade() throws IOException {
        int runs = Integer.parseInt(property("eval.runs", "3"));
        String only = property("eval.only", "");
        boolean saveBaseline = Boolean.parseBoolean(property("eval.saveBaseline", "false"));
        double maxDrop = Double.parseDouble(property("eval.maxDrop", "3"));

        checkReadyToSpendMoney();

        LabelCases.Loaded loaded = LabelCases.load(LABELS, IMAGES, only);
        loaded.skipped().forEach(s -> System.out.println("SKIPPED " + s));
        assertThat(loaded.cases()).as("products to evaluate (see SKIPPED lines)").isNotEmpty();
        System.out.printf("Evaluating %d products x %d runs = %d model calls (plus retries)%n",
                loaded.cases().size(), runs, loaded.cases().size() * runs);

        String[] modelSeen = {null};
        EvalResult result = new EvalRunner(System.out::println).run("reading", loaded.cases(),
                subject(modelSeen), new LabelScorer(new ReadingComparison()), runs);

        var baseline = Baseline.load(BASELINE);
        var regressions = baseline.map(b -> Baseline.compare(result.metrics(), b, maxDrop)).orElse(null);

        Map<String, String> info = new LinkedHashMap<>();
        info.put("model", modelSeen[0] == null ? "(no successful call)" : modelSeen[0]);
        info.put("prompt", "read-label.md, sha256 " + sha256(Path.of("src/main/resources/prompts/read-label.md")));
        info.put("answer key", "labels.json, sha256 " + sha256(LABELS));
        info.put("skipped", loaded.skipped().isEmpty() ? "none" : String.join("; ", loaded.skipped()));
        info.put("est. cost", String.format(Locale.ROOT, "$%.4f total, $%.4f per run (list price)",
                cost(result), cost(result) / Math.max(1, result.runs().size())));

        Path report = Reports.write(REPORTS, result, regressions, info);
        System.out.println();
        System.out.println(Reports.text(result, regressions, info));
        System.out.println("Report: " + report.toAbsolutePath().normalize());

        if (saveBaseline) {
            Baseline.save(BASELINE, result);
            System.out.println("Baseline saved: " + BASELINE.toAbsolutePath().normalize());
            return;
        }
        if (regressions != null) {
            assertThat(regressions.stream().filter(Baseline.Regression::gated).toList())
                    .as("overall scores that dropped more than %s points below the baseline", maxDrop)
                    .isEmpty();
        }
    }

    /** One run = one call to the app's real LabelReader, exactly as an upload would do it. */
    private Subject<LabelCases.LabelPhotos, LabelReading> subject(String[] modelSeen) {
        return photos -> {
            List<ImageInput> images = new ArrayList<>();
            for (Path f : photos.files()) {
                images.add(new ImageInput(readBytes(f), LabelCases.LabelPhotos.contentType(f)));
            }
            int before = calls.size();
            int retries = 0;
            while (true) {
                long start = System.nanoTime();
                ReadOutcome outcome = reader.read(UUID.randomUUID(), images);
                long ms = (System.nanoTime() - start) / 1_000_000;

                if (!outcome.success() && isInfrastructureProblem(outcome.failureReason()) && retries < MAX_INFRA_RETRIES) {
                    retries++;
                    System.out.println("  rate limit / unavailable, pausing " + (INFRA_PAUSE_MS * retries / 1000) + " s");
                    sleep(INFRA_PAUSE_MS * retries);
                    continue;
                }
                List<AiCallRecord> made = calls.since(before);
                long in = made.stream().mapToLong(c -> c.inputTokens() == null ? 0 : c.inputTokens()).sum();
                long out = made.stream().mapToLong(c -> c.outputTokens() == null ? 0 : c.outputTokens()).sum();
                if (outcome.success()) {
                    modelSeen[0] = outcome.model();
                    return Attempt.answered(outcome.reading(), ms, in, out, retries);
                }
                return Attempt.failed(outcome.failureReason() + endOf(outcome.rawResponse()), ms, in, out, retries);
            }
        };
    }

    /**
     * The last characters of an unusable answer, so the report shows HOW it went wrong
     * (day 5: p003's answer was cut off mid-sentence because the model kept repeating itself).
     */
    static String endOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String flat = raw.replaceAll("\\s+", " ").strip();
        return " | answer ended with: ..." + (flat.length() > 200 ? flat.substring(flat.length() - 200) : flat);
    }

    /**
     * Problems with the service, not with the answer: retry instead of counting a wrong reading.
     *
     * A timeout is NOT retried: on day 5, p003 timed out 4 times in a row (11 minutes). When the
     * same input times out every time, the input causes it, and a user would see a failure too.
     */
    static boolean isInfrastructureProblem(String reason) {
        if (reason == null || !reason.startsWith("AI call failed")) {
            return false; // the model answered; the answer was bad. That counts.
        }
        String r = reason.toLowerCase(Locale.ROOT);
        return r.contains("429") || r.contains("rate limit") || r.contains("too many requests")
                || r.contains("503");
    }

    /** Fail in seconds, before any paid call, if the key or the comparison rule is missing. */
    private void checkReadyToSpendMoney() {
        String key = env.getProperty("spring.ai.openai.api-key", "");
        String endpoint = env.getProperty("spring.ai.openai.base-url", "");
        if (key.isBlank() || key.startsWith("missing-") || endpoint.contains(".invalid")) {
            fail("No Azure key or endpoint: the eval calls the real model. Put AZURE_OPENAI_API_KEY and "
                    + "AZURE_OPENAI_ENDPOINT in .env at the project root.");
        }
        try {
            new ReadingComparison().sameNumber("salt", 1.0, 1.0);
        } catch (UnsupportedOperationException e) {
            fail("ReadingComparison isn't written yet (day 5, step 2). Nothing was sent to the model.");
        }
    }

    private double cost(EvalResult r) {
        return (r.totalInputTokens() * PRICE_IN + r.totalOutputTokens() * PRICE_OUT) / 1_000_000;
    }

    /** System property (set by the eval profile) or default. Empty counts as not set. */
    private static String property(String name, String fallback) {
        String v = System.getProperty(name);
        return v == null || v.isBlank() ? fallback : v.strip();
    }

    private static byte[] readBytes(Path f) {
        try {
            return Files.readAllBytes(f);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Short fingerprint of a file: two reports with the same prompt hash used the same prompt. */
    private static String sha256(Path f) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (IOException | NoSuchAlgorithmException e) {
            return "unknown (" + e.getMessage() + ")";
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
