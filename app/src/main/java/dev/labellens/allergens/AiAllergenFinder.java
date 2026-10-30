package dev.labellens.allergens;

import dev.labellens.ai.ModelReply;
import dev.labellens.ai.TextModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Asks a model which EU allergens an ingredients text contains.
 *
 * The instruction (prompts/find-allergens.md) is the SAME one used in the fine-tuning file
 * (DATA_PLAN): the base models and the fine-tuned one must be compared on equal terms.
 *
 * Only the ingredients part is sent: MayContainSplitter removes the "may contain" statement first.
 * No retry: a broken answer is a failure the eval counts, the same way for every model.
 */
@Service
public class AiAllergenFinder {

    private final String instructions;
    private final JsonMapper json = JsonMapper.builder().build();

    public AiAllergenFinder(@Value("classpath:prompts/find-allergens.md") Resource prompt) {
        this.instructions = load(prompt).strip();
    }

    /**
     * @param allergens  the EU-14 tags found (empty if none); null if the answer was unusable
     * @param ignored    tags the model returned that aren't EU-14 (e.g. "en:pork"): dropped, but reported
     * @param problem    why the answer was unusable, or null
     */
    public record Result(Set<String> allergens, List<String> ignored, String problem, ModelReply reply) {
        public boolean ok() {
            return problem == null;
        }
    }

    /** The exact instruction sent to the model: the fine-tuning file must use the same one. */
    public String instructions() {
        return instructions;
    }

    public Result find(TextModel model, String ingredients) {
        ModelReply reply = model.ask(instructions, ingredients == null ? "" : ingredients);
        return parse(reply);
    }

    Result parse(ModelReply reply) {
        String text = reply.text();
        if (text == null || text.isBlank()) {
            return new Result(null, List.of(), "the answer was empty", reply);
        }
        JsonNode root;
        try {
            root = json.readTree(stripCodeFences(text));
        } catch (JacksonException e) {
            return new Result(null, List.of(), "the answer is not valid JSON", reply);
        }
        JsonNode list = root == null ? null : root.get("allergens");
        if (list == null || !list.isArray()) {
            return new Result(null, List.of(), "the answer has no \"allergens\" list", reply);
        }
        Set<String> found = new TreeSet<>();
        List<String> ignored = new ArrayList<>();
        for (JsonNode item : list) {
            String tag = item.asString().strip().toLowerCase(Locale.ROOT);
            if (AllergenWordList.EU_14.contains(tag)) {
                found.add(tag);
            } else {
                ignored.add(tag);
            }
        }
        return new Result(found, ignored, null, reply);
    }

    /** Models often wrap JSON in ```json ... ``` even when told not to. */
    static String stripCodeFences(String text) {
        String t = text.strip();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return t.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return t;
    }

    private static String load(Resource prompt) {
        try {
            return prompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read prompt " + prompt, e);
        }
    }
}
