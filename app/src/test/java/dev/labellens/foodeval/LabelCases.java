package dev.labellens.foodeval;

import dev.labellens.eval.EvalCase;
import dev.labellens.reading.LabelReading;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns labels.json into eval cases: input = the product's photos, expected = the typed values,
 * tags = its situations ("arabic", "blurry"...).
 *
 * Products that can't be tested (template with no type, photo file missing) are skipped
 * and listed with the reason, never silently dropped.
 */
public final class LabelCases {

    /** The photos of one product, read from disk only when the AI step runs. */
    public record LabelPhotos(List<Path> files) {

        public static String contentType(Path file) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            if (name.endsWith(".png")) {
                return "image/png";
            }
            return null;
        }
    }

    public record Loaded(List<EvalCase<LabelPhotos, LabelReading>> cases, List<String> skipped) {
    }

    private LabelCases() {
    }

    /**
     * @param only product ids to keep, comma-separated ("p004,p007"); blank = all
     */
    public static Loaded load(Path labelsJson, Path imagesDir, String only) throws IOException {
        JsonNode root = JsonMapper.builder().build().readTree(Files.readString(labelsJson, StandardCharsets.UTF_8));
        Set<String> wanted = only == null || only.isBlank()
                ? Set.of()
                : Arrays.stream(only.split(",")).map(String::strip).collect(Collectors.toSet());

        List<EvalCase<LabelPhotos, LabelReading>> cases = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (JsonNode p : root.get("products")) {
            String id = text(p, "id");
            if (!wanted.isEmpty() && !wanted.contains(id)) {
                continue;
            }
            if (text(p, "type") == null) {
                skipped.add(id + ": no type (template not filled in yet)");
                continue;
            }
            // Distinct files, in a fixed order: nutrition table, ingredients, front.
            Set<String> names = new LinkedHashSet<>();
            JsonNode photos = p.get("photos");
            for (String kind : List.of("nutrition", "ingredients", "front")) {
                String name = photos == null ? null : text(photos, kind);
                if (name != null) {
                    names.add(name);
                }
            }
            List<Path> files = names.stream().map(imagesDir::resolve).toList();
            List<String> problems = new ArrayList<>();
            if (files.isEmpty()) {
                problems.add("no photos listed");
            }
            for (Path f : files) {
                if (!Files.exists(f)) {
                    problems.add(f.getFileName() + " not found");
                } else if (LabelPhotos.contentType(f) == null) {
                    problems.add(f.getFileName() + " is not a .jpg or .png");
                }
            }
            if (!problems.isEmpty()) {
                skipped.add(id + ": " + String.join(", ", problems));
                continue;
            }

            Set<String> tags = new LinkedHashSet<>();
            JsonNode situations = p.get("situations");
            if (situations != null) {
                situations.forEach(s -> tags.add(s.asString()));
            }
            cases.add(new EvalCase<>(id, new LabelPhotos(files), expected(p), tags));
        }
        return new Loaded(cases, skipped);
    }

    /** The typed values, in the same shape as what the app produces from the AI's answer. */
    static LabelReading expected(JsonNode p) {
        JsonNode v = p.get("per_100g");
        return new LabelReading(
                text(p, "name"),
                text(p, "brand"),
                text(p, "type"),
                number(v, "energy_kj"),
                number(v, "fat"),
                number(v, "saturated_fat"),
                number(v, "carbohydrates"),
                number(v, "sugars"),
                number(v, "fibre"),
                number(v, "protein"),
                number(v, "salt"),
                text(p, "ingredients_text"),
                text(p, "ingredients_language"),
                null);
    }

    /** null for a missing field, JSON null, or an empty string. */
    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asString();
        return s.isBlank() ? null : s;
    }

    private static Double number(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asDouble();
    }
}
