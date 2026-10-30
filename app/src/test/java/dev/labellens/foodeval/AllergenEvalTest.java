package dev.labellens.foodeval;

import dev.labellens.TestcontainersConfiguration;
import dev.labellens.allergens.AiAllergenFinder;
import dev.labellens.ai.TextModels;
import dev.labellens.eval.Baseline;
import dev.labellens.eval.EvalCase;
import dev.labellens.eval.EvalResult;
import dev.labellens.eval.EvalRunner;
import dev.labellens.eval.Reports;
import dev.labellens.eval.Subject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Test 3: allergens, on the Open Food Facts test set, for every setup.
 *
 *   mvn -pl app test -Peval "-Dtest=AllergenEvalTest" "-Deval.limit=50"      50 products, cheap trial
 *   mvn -pl app test -Peval "-Dtest=AllergenEvalTest"                         all 500
 *   ... "-Deval.set=ar"                                                       the Arabic slice
 *   ... "-Deval.setups=wordlist,ai-gpt-4.1-nano"                              chosen setups only
 *   ... "-Deval.saveBaseline=true"                                            save each setup's baseline
 *
 * Reports: eval-food/reports/allergens-{set}-{setup}-{date}.txt, one per setup.
 * Same eval-core harness as the label reader: only the cases, the subject and the scorer changed.
 */
@Tag("eval")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class AllergenEvalTest {

    private static final Path EVAL_FOOD = Path.of("..", "eval-food");
    private static final Path REPORTS = EVAL_FOOD.resolve("reports");
    private static final Path BASELINES = EVAL_FOOD.resolve("baselines");
    private static final String DEFAULT_SETUPS =
            "wordlist,ai-gpt-4.1-nano,ai-gpt-4.1,combined-gpt-4.1-nano,combined-gpt-4.1";

    /** List prices in US$ per million tokens (input, output). Check the Azure pricing page if they change. */
    private static final Map<String, double[]> PRICES = Map.of(
            "gpt-4.1", new double[]{2.00, 8.00},
            "gpt-4.1-mini", new double[]{0.40, 1.60},
            "gpt-4.1-nano", new double[]{0.10, 0.40});

    @Autowired
    AiAllergenFinder finder;

    @Autowired
    TextModels models;

    @Autowired
    Environment env;

    @Test
    void allergens() throws IOException {
        String set = property("eval.set", "fr");
        int limit = Integer.parseInt(property("eval.limit", "0"));
        int runs = Integer.parseInt(property("eval.allergens.runs", "1"));
        boolean saveBaseline = Boolean.parseBoolean(property("eval.saveBaseline", "false"));
        double maxDrop = Double.parseDouble(property("eval.maxDrop", "3"));
        List<String> setups = Arrays.stream(property("eval.setups", DEFAULT_SETUPS).split(","))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();

        Path file = EVAL_FOOD.resolve("data/off/" + (set.equals("ar") ? "allergen_test_ar.jsonl" : "allergen_test.jsonl"));
        List<EvalCase<String, AllergenAnswer>> cases = AllergenCases.load(file, limit);
        assertThat(cases).as("cases in " + file).isNotEmpty();

        boolean wordList = AllergenSetups.wordListAvailable();
        if (!wordList) {
            System.out.println("AllergenWordList isn't written yet: skipping 'wordlist' and 'combined-*' setups,"
                    + " and the \"may contain\" checks.");
        }

        List<String> blocking = new ArrayList<>();
        for (String setup : setups) {
            if (!wordList && (setup.equals("wordlist") || setup.startsWith("combined-"))) {
                continue;
            }
            String deployment = setup.equals("wordlist") ? null : setup.substring(setup.indexOf('-') + 1);
            String modelSeen = "(no AI)";
            if (deployment != null) {
                checkReadyToSpendMoney();
                // One tiny call first: proves the deployment really switched (Azure reports the model behind it).
                modelSeen = models.forDeployment(deployment).ask("Answer with the word OK.", "ping").model();
                System.out.println(setup + ": deployment " + deployment + " answered as " + modelSeen);
            }
            Subject<String, AllergenAnswer> subject = switch (setup.substring(0, setup.indexOf('-') < 0 ? setup.length() : setup.indexOf('-'))) {
                case "wordlist" -> AllergenSetups.wordList();
                case "ai" -> AllergenSetups.ai(finder, models.forDeployment(deployment), wordList);
                case "combined" -> AllergenSetups.combined(finder, models.forDeployment(deployment));
                default -> throw new IllegalArgumentException("Unknown setup: " + setup
                        + " (expected wordlist, ai-<deployment> or combined-<deployment>)");
            };

            String suite = "allergens-" + set + "-" + setup;
            System.out.printf("%n=== %s: %d products x %d run(s) ===%n", suite, cases.size(), runs);
            // Progress: every 50th product and every failure (500 lines per setup would drown the console).
            EvalResult result = new EvalRunner(line -> {
                if (line.contains("FAILED") || line.matches("\\[\\d*(00|50)/.*")) {
                    System.out.println(line);
                }
            }).run(suite, cases, subject, new AllergenScorer(), runs);

            Path baselineFile = BASELINES.resolve(suite + ".json");
            var regressions = Baseline.load(baselineFile)
                    .map(b -> Baseline.compare(result.metrics(), b, maxDrop)).orElse(null);
            Map<String, String> info = new LinkedHashMap<>();
            info.put("setup", setup);
            info.put("model", modelSeen);
            info.put("test set", file.getFileName() + (limit > 0 ? " (first " + limit + " only)" : ""));
            info.put("est. cost", deployment == null ? "$0 (no AI)" : String.format(Locale.ROOT, "$%.4f", cost(result, deployment)));
            info.put("main score", "caught:all = allergens really there that were found (a miss is the dangerous mistake)");

            Path report = Reports.write(REPORTS, result, regressions, info);
            System.out.println(summaryLine(result) + "\nReport: " + report.toAbsolutePath().normalize());

            if (saveBaseline && !result.failedRuns().isEmpty()) {
                // Day 6, run 3: a network outage failed 114 calls and the broken scores were saved
                // as the baseline. Failed calls say nothing about the model: rerun this setup instead.
                System.out.printf("NOT saving the baseline for %s: %d run(s) had no usable answer."
                        + " Rerun with -Deval.setups=%s.%n", suite, result.failedRuns().size(), setup);
                blocking.add(suite + " baseline not saved (" + result.failedRuns().size() + " failed runs)");
            } else if (saveBaseline) {
                Baseline.save(baselineFile, result);
            } else if (regressions != null) {
                regressions.stream().filter(Baseline.Regression::gated)
                        .forEach(g -> blocking.add(suite + " " + g.metric()));
            }
        }
        assertThat(blocking).as("scores that dropped more than %s points below their baseline", maxDrop).isEmpty();
    }

    private static String summaryLine(EvalResult r) {
        return String.format(Locale.ROOT, "answered %s | caught %s | correct alarms %s | whole set right %s",
                pct(r.answered()), pct(r.score("caught:all")), pct(r.score("correct-alarm:all")), pct(r.score("allergens")));
    }

    private static String pct(EvalResult.Score s) {
        return s.total() == 0 ? "-" : String.format(Locale.ROOT, "%.1f%% (%d/%d)", s.percent(), s.passed(), s.total());
    }

    private static double cost(EvalResult r, String deployment) {
        double[] price = PRICES.getOrDefault(deployment, new double[]{0, 0});
        return (r.totalInputTokens() * price[0] + r.totalOutputTokens() * price[1]) / 1_000_000;
    }

    private void checkReadyToSpendMoney() {
        String key = env.getProperty("spring.ai.openai.api-key", "");
        String endpoint = env.getProperty("spring.ai.openai.base-url", "");
        if (key.isBlank() || key.startsWith("missing-") || endpoint.contains(".invalid")) {
            fail("No Azure key or endpoint in .env: the AI setups call the real models.");
        }
    }

    private static String property(String name, String fallback) {
        String v = System.getProperty(name);
        return v == null || v.isBlank() ? fallback : v.strip();
    }
}