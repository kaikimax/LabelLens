package dev.labellens.foodeval;

import dev.labellens.TestcontainersConfiguration;
import dev.labellens.ai.TextModel;
import dev.labellens.ai.TextModels;
import dev.labellens.allergens.AiAllergenFinder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Day 7: builds the fine-tuning files from training/data/allergen_train.jsonl.
 *
 *   mvn -pl app test -Peval "-Dtest=FineTuneFileTest"                        all 3,000 (~$1.50, ~30 min)
 *   ... "-Dfinetune.limit=50"                                                 trial on 50
 *   ... "-Dfinetune.threads=2"                                                fewer parallel calls (429s)
 *
 * For each product: the ingredients part (same splitter as the eval) goes to the teacher (gpt-4.1)
 * with the SAME instruction as the eval. The product is kept only when the teacher's allergens equal
 * Open Food Facts' (decisions log, 2026-10-30).
 *
 * The teacher's answers are saved as they come (teacher-gpt-4.1.jsonl), so a rerun after a network
 * failure only pays for the products still missing.
 *
 * Writes, in training/data/finetune/: train.jsonl, validation.jsonl (Azure chat format),
 * disagreements.jsonl (dropped products, to read), FINETUNE_DATA_CARD.md.
 */
@Tag("eval")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class FineTuneFileTest {

    private static final Path TRAINING = Path.of("..", "training", "data", "allergen_train.jsonl");
    private static final Path OUT = Path.of("..", "training", "data", "finetune");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    AiAllergenFinder finder;

    @Autowired
    TextModels models;

    @Autowired
    Environment env;

    record Product(String code, String input, Set<String> offKey) {
    }

    @Test
    void buildFineTuneFiles() throws Exception {
        String teacherName = property("finetune.teacher", "gpt-4.1");
        int limit = Integer.parseInt(property("finetune.limit", "0"));
        int threads = Integer.parseInt(property("finetune.threads", "4"));
        checkReadyToSpendMoney();
        Files.createDirectories(OUT);

        // ---- 1. Products and their model input
        List<Product> products = new ArrayList<>();
        int noIngredients = 0;
        for (String line : Files.readAllLines(TRAINING, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode p = JSON.readTree(line);
            String input = FineTuneExamples.input(p.get("text").asString());
            if (input == null) {
                noIngredients++;
                continue;
            }
            products.add(new Product(p.get("code").asString(), input, tags(p.get("allergens"))));
            if (limit > 0 && products.size() == limit) {
                break;
            }
        }
        assertThat(products).as("products in " + TRAINING).isNotEmpty();

        // ---- 2. Teacher answers: cached ones first, then ask for the rest
        Path cacheFile = OUT.resolve("teacher-" + teacherName + ".jsonl");
        Map<String, Set<String>> teacher = new ConcurrentHashMap<>(readCache(cacheFile));
        List<Product> missing = products.stream().filter(p -> !teacher.containsKey(p.code())).toList();
        System.out.printf("%d products, %d answers cached, %d to ask %s (%d threads)%n",
                products.size(), products.size() - missing.size(), missing.size(), teacherName, threads);

        TextModel model = models.forDeployment(teacherName);
        String modelSeen = model.ask("Answer with the word OK.", "ping").model();
        System.out.println("teacher: deployment " + teacherName + " answered as " + modelSeen);

        AtomicInteger done = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger inTokens = new AtomicInteger();
        AtomicInteger outTokens = new AtomicInteger();
        try (BufferedWriter cache = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (Product p : missing) {
                pool.execute(() -> {
                    AiAllergenFinder.Result r = askWithRetry(model, p.input());
                    if (r != null && r.reply() != null) {
                        add(inTokens, r.reply().inputTokens());
                        add(outTokens, r.reply().outputTokens());
                    }
                    if (r == null || !r.ok()) {
                        failed.incrementAndGet();
                        System.out.println("FAILED " + p.code() + ": " + (r == null ? "no answer" : r.problem()));
                    } else {
                        teacher.put(p.code(), r.allergens());
                        appendToCache(cache, p.code(), r.allergens());
                    }
                    int n = done.incrementAndGet();
                    if (n % 100 == 0) {
                        System.out.printf("[%d/%d] asked, %d failed%n", n, missing.size(), failed.get());
                    }
                });
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(3, TimeUnit.HOURS)).as("teacher calls finished").isTrue();
        }

        // ---- 3. Keep the products where the teacher and OFF agree; split train / validation
        String instructions = finder.instructions();
        List<String> train = new ArrayList<>();
        List<String> validation = new ArrayList<>();
        List<String> disagreements = new ArrayList<>();
        Map<String, Integer> keptPerAllergen = new TreeMap<>();
        int noAnswer = 0;
        int keptEmpty = 0;
        for (Product p : products) {
            Set<String> answer = teacher.get(p.code());
            if (answer == null) {
                noAnswer++;
                continue;
            }
            if (!FineTuneExamples.agree(p.offKey(), answer)) {
                ObjectNode d = JSON.createObjectNode();
                d.put("code", p.code()).put("input", p.input());
                d.putPOJO("off", new TreeSet<>(p.offKey()));
                d.putPOJO("teacher", new TreeSet<>(answer));
                disagreements.add(JSON.writeValueAsString(d));
                continue;
            }
            String line = FineTuneExamples.line(instructions, p.input(), answer);
            (FineTuneExamples.isValidation(p.code()) ? validation : train).add(line);
            answer.forEach(a -> keptPerAllergen.merge(a, 1, Integer::sum));
            if (answer.isEmpty()) {
                keptEmpty++;
            }
        }

        Files.write(OUT.resolve("train.jsonl"), train, StandardCharsets.UTF_8);
        Files.write(OUT.resolve("validation.jsonl"), validation, StandardCharsets.UTF_8);
        Files.write(OUT.resolve("disagreements.jsonl"), disagreements, StandardCharsets.UTF_8);
        String card = dataCard(teacherName, modelSeen, products.size(), noIngredients, noAnswer,
                disagreements.size(), train.size(), validation.size(), keptEmpty, keptPerAllergen,
                inTokens.get(), outTokens.get(), train, validation);
        Files.writeString(OUT.resolve("FINETUNE_DATA_CARD.md"), card, StandardCharsets.UTF_8);
        System.out.println(card);

        if (noAnswer > 0) {
            fail(noAnswer + " products have no teacher answer yet (failed calls). Rerun: only those are asked again.");
        }
    }

    // ---- Teacher calls

    /** Retries rate limits and network errors (not bad answers: those are the teacher's own). */
    private AiAllergenFinder.Result askWithRetry(TextModel model, String input) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return finder.find(model, input);
            } catch (RuntimeException e) {
                String why = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
                boolean retryable = why.contains("429") || why.contains("rate") || why.contains("503")
                        || why.contains("request failed") || why.contains("timeout");
                if (!retryable || attempt == 5) {
                    System.out.println("giving up: " + e.getMessage());
                    return null;
                }
                sleep(attempt * 5_000L);
            }
        }
        return null;
    }

    private static synchronized void appendToCache(BufferedWriter cache, String code, Set<String> allergens) {
        try {
            ObjectNode line = JSON.createObjectNode();
            line.put("code", code);
            line.putPOJO("allergens", new TreeSet<>(allergens));
            cache.write(JSON.writeValueAsString(line));
            cache.newLine();
            cache.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Set<String>> readCache(Path file) throws IOException {
        Map<String, Set<String>> cached = new ConcurrentHashMap<>();
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    JsonNode n = JSON.readTree(line);
                    cached.put(n.get("code").asString(), tags(n.get("allergens")));
                }
            }
        }
        return cached;
    }

    // ---- Report

    private static String dataCard(String teacher, String modelSeen, int products, int noIngredients, int noAnswer,
                                   int disagreements, int train, int validation, int keptEmpty,
                                   Map<String, Integer> perAllergen, int inTokens, int outTokens,
                                   List<String> trainLines, List<String> validationLines) {
        int kept = train + validation;
        long chars = trainLines.stream().mapToLong(String::length).sum();
        StringBuilder sb = new StringBuilder();
        sb.append("# Fine-tuning data (allergens)\n\n");
        sb.append("Built ").append(LocalDate.now()).append(" by `FineTuneFileTest` from `training/data/allergen_train.jsonl`.\n");
        sb.append("Teacher: `").append(teacher).append("` (answered as ").append(modelSeen).append(").\n");
        sb.append("Rule (decisions log, 2026-10-30): keep a product only when the teacher's allergens equal Open Food Facts'.\n\n");
        sb.append("| Step | Products |\n|---|---|\n");
        sb.append("| Training products with ingredients | ").append(products).append(" |\n");
        sb.append("| Skipped: only a \"may contain\" statement | ").append(noIngredients).append(" |\n");
        sb.append("| No teacher answer (failed calls) | ").append(noAnswer).append(" |\n");
        sb.append("| Dropped: teacher and OFF disagree (`disagreements.jsonl`) | ").append(disagreements).append(" |\n");
        sb.append("| **Kept** | **").append(kept).append("** (").append(percent(kept, products - noAnswer)).append(" of answered) |\n");
        sb.append("| of which with no allergen | ").append(keptEmpty).append(" |\n");
        sb.append("| `train.jsonl` | ").append(train).append(" |\n");
        sb.append("| `validation.jsonl` | ").append(validation).append(" |\n\n");
        sb.append("Kept examples per allergen:\n\n| Allergen | Examples |\n|---|---|\n");
        perAllergen.forEach((a, n) -> sb.append("| ").append(a).append(" | ").append(n).append(" |\n"));
        sb.append("\nSize: about ").append(chars / 4 / 1000).append("K tokens per epoch in train.jsonl (characters / 4).\n");
        sb.append("Teacher calls this run: ").append(inTokens).append(" input, ").append(outTokens).append(" output tokens.\n\n");
        sb.append("Known limits:\n");
        sb.append("- Keeping only agreements drops the hard cases where OFF is right and the teacher wrong, so the\n");
        sb.append("  fine-tuned model can't learn to beat its teacher on those.\n");
        sb.append("- Where OFF and the teacher are wrong together, the error stays in the training data.\n");
        sb.append("- The test set (`eval-food/data/off/allergen_test.jsonl`) never appears here: the split was made by\n");
        sb.append("  `build_allergen_sets.py`, and duplicates were removed across both.\n");
        return sb.toString();
    }

    private static String percent(int part, int whole) {
        return whole == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * part / whole);
    }

    // ---- Small helpers

    private static Set<String> tags(JsonNode array) {
        Set<String> result = new TreeSet<>();
        if (array != null) {
            array.forEach(t -> result.add(t.asString()));
        }
        return result;
    }

    private static void add(AtomicInteger counter, Integer value) {
        if (value != null) {
            counter.addAndGet(value);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkReadyToSpendMoney() {
        String key = env.getProperty("spring.ai.openai.api-key", "");
        String endpoint = env.getProperty("spring.ai.openai.base-url", "");
        if (key.isBlank() || key.startsWith("missing-") || endpoint.contains(".invalid")) {
            fail("No Azure key or endpoint in .env: the teacher is a real model.");
        }
    }

    private static String property(String name, String fallback) {
        String v = System.getProperty(name);
        return v == null || v.isBlank() ? fallback : v.strip();
    }
}
