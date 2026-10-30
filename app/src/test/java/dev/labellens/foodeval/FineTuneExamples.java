package dev.labellens.foodeval;

import dev.labellens.allergens.MayContainSplitter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.TreeSet;

/**
 * The pure parts of building the fine-tuning file (day 7), kept apart so they can be unit-tested
 * without calling a model.
 *
 * Decisions log, 2026-10-30: a training product is kept only when the teacher model (gpt-4.1)
 * and Open Food Facts give the SAME allergens. Day 6 showed the OFF key is wrong in about 3 of
 * every 4 disagreements, so training on it as is would teach nano the key's mistakes.
 */
public final class FineTuneExamples {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FineTuneExamples() {
    }

    /**
     * What the model is shown: the ingredients part only, exactly as AiAllergenFinder gets it in the
     * eval and in the app. Null when nothing is left (the text was only a "may contain" statement).
     */
    public static String input(String fullText) {
        String ingredients = MayContainSplitter.split(fullText).ingredients();
        return ingredients == null || ingredients.isBlank() ? null : ingredients;
    }

    /** Same set of tags, order ignored. */
    public static boolean agree(Set<String> offKey, Set<String> teacher) {
        return new TreeSet<>(offKey).equals(new TreeSet<>(teacher));
    }

    /**
     * About 10% of products, chosen by barcode so a rebuild gives the same split.
     * Salted: build_allergen_sets.py ORDERS products by md5(barcode), so an unsalted md5 would
     * pick products from fixed regions of that order (the 50-product trial got 0 validation).
     */
    public static boolean isValidation(String code) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(("validation:" + code).getBytes(StandardCharsets.UTF_8));
            return Byte.toUnsignedInt(hash[0]) % 10 == 0;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The answer exactly as the model must learn to write it: {"allergens":["en:gluten","en:milk"]}, sorted. */
    public static String answer(Set<String> allergens) {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode list = root.putArray("allergens");
        new TreeSet<>(allergens).forEach(list::add);
        return JSON.writeValueAsString(root);
    }

    /** One line of Azure's chat fine-tuning format. */
    public static String line(String instructions, String input, Set<String> allergens) {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", instructions);
        messages.addObject().put("role", "user").put("content", input);
        messages.addObject().put("role", "assistant").put("content", answer(allergens));
        return JSON.writeValueAsString(root);
    }
}