package dev.labellens.foodeval;

import dev.labellens.eval.EvalCase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns allergen_test.jsonl (built by training/build_allergen_sets.py) into eval cases:
 * input = the full cleaned text (with any "may contain" sentence), expected = OFF's tags.
 *
 * Tags, to split the scores: "html" (the text had allergen markup, now removed),
 * "with-traces", "no-allergen".
 */
public final class AllergenCases {

    private AllergenCases() {
    }

    /** @param limit keep only the first N products (0 = all): for cheap trial runs. */
    public static List<EvalCase<String, AllergenAnswer>> load(Path jsonl, int limit) throws IOException {
        JsonMapper json = JsonMapper.builder().build();
        List<EvalCase<String, AllergenAnswer>> cases = new ArrayList<>();
        for (String line : Files.readAllLines(jsonl, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode p = json.readTree(line);
            Set<String> allergens = tags(p.get("allergens"));
            Set<String> traces = tags(p.get("traces"));
            Set<String> caseTags = new LinkedHashSet<>();
            if (p.has("had_html") && p.get("had_html").asBoolean()) {
                caseTags.add("html");
            }
            if (!traces.isEmpty()) {
                caseTags.add("with-traces");
            }
            if (allergens.isEmpty()) {
                caseTags.add("no-allergen");
            }
            cases.add(new EvalCase<>(p.get("code").asString(), p.get("text").asString(),
                    new AllergenAnswer(allergens, traces), caseTags));
            if (limit > 0 && cases.size() == limit) {
                break;
            }
        }
        return cases;
    }

    private static Set<String> tags(JsonNode array) {
        Set<String> result = new TreeSet<>();
        if (array != null) {
            array.forEach(t -> result.add(t.asString()));
        }
        return result;
    }
}
