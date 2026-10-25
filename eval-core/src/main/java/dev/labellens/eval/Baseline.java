package dev.labellens.eval;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The last accepted scores, saved in a JSON file and committed with the code.
 * Every new eval is compared with it: a score that drops by more than the allowed
 * number of points is a regression.
 *
 * Only OVERALL scores block ("salt"). Per-tag scores ("salt[arabic]") are reported but
 * don't block: they're computed on a few products, so one product moves them a lot.
 */
public final class Baseline {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * @param gated true if this regression should fail the eval (overall scores only)
     */
    public record Regression(String metric, double baseline, double current, boolean gated) {
        public double drop() {
            return baseline - current;
        }
    }

    private Baseline() {
    }

    public static Optional<Map<String, Double>> load(Path file) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            JsonNode metrics = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8)).get("metrics");
            Map<String, Double> result = new LinkedHashMap<>();
            if (metrics != null) {
                metrics.properties().forEach(e -> result.put(e.getKey(), e.getValue().asDouble()));
            }
            return Optional.of(result);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read baseline " + file, e);
        }
    }

    public static void save(Path file, EvalResult result) {
        ObjectNode root = JSON.createObjectNode();
        root.put("suite", result.suite());
        root.put("savedAt", Instant.now().toString());
        root.put("runsPerCase", result.runsPerCase());
        root.put("cases", result.caseIds().size());
        ObjectNode metrics = root.putObject("metrics");
        result.metrics().forEach((k, v) -> {
            if (!v.isNaN()) {
                metrics.put(k, Math.round(v * 10) / 10.0);
            }
        });
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, JSON.writer().withDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write baseline " + file, e);
        }
    }

    /** Every metric that dropped by more than maxDropPoints (percentage points). */
    public static List<Regression> compare(Map<String, Double> current, Map<String, Double> baseline, double maxDropPoints) {
        List<Regression> result = new ArrayList<>();
        baseline.forEach((metric, before) -> {
            Double now = current.get(metric);
            if (now == null || now.isNaN() || before == null) {
                return; // metric no longer measured (e.g. a tag with no products left)
            }
            if (before - now > maxDropPoints) {
                result.add(new Regression(metric, before, now, !metric.contains("[")));
            }
        });
        return result;
    }
}
