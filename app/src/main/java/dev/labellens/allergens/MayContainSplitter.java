package dev.labellens.allergens;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Splits an ingredients text into the ingredients themselves and the "may contain" statement.
 *
 * Decisions log, 2026-10-26: "may contain" is only what's printed, never guessed, and Java
 * finds it (not the AI). Code finds a fixed list of phrases the same way every time; the AI
 * then only sees the ingredients part, so it can't mix traces into the allergens (p010).
 *
 * Known limit: wording that isn't in the list stays in the ingredients part. The eval shows
 * it (a trace counted as an allergen), and the phrase gets added here.
 *
 * A statement ends at the end of its sentence, not at the end of the text (day 6, review round 2):
 * labels often go on after it ("Peut contenir du lait. Sans blé. Sans oeufs.", or the same list
 * in another language), and those words were being read as traces. Several statements in one
 * text are all collected.
 */
public final class MayContainSplitter {

    /** Where the "may contain" statement starts. Lowercase; the text is lowercased before searching. */
    static final List<String> PHRASES = List.of(
            // French
            "il peut contenir", "ce produit peut contenir", "peut contenir", "peut également contenir", "peut aussi contenir",
            "traces éventuelles", "traces possibles", "présence éventuelle", "présence possible", "presence eventuelle", "presence possible",
            "traces de", "traces d'",
            "fabriqué dans un atelier", "produit dans un atelier", "élaboré dans un atelier",
            "fabriqué dans une usine", "produit dans une usine",
            // English
            "this product may contain", "may contain", "may also contain", "traces of", "may be present",
            "made in a factory", "produced in a factory", "manufactured in a factory",
            "made on equipment", "produced on equipment",
            // Arabic
            "قد يحتوي", "قد يحتوى", "قد تحتوي", "يحتوي على آثار", "آثار من");

    public record Split(String ingredients, String mayContain) {
    }

    private MayContainSplitter() {
    }

    /** mayContain is null when no statement is found. Never returns null itself. */
    public static Split split(String text) {
        if (text == null || text.isBlank()) {
            return new Split(text, null);
        }
        List<String> ingredients = new ArrayList<>();
        List<String> statements = new ArrayList<>();
        String rest = text;
        int start;
        while ((start = firstPhrase(rest)) >= 0) {
            // "…, sel. Peut contenir…" → "…, sel." ; "…, sel, peut contenir…" → "…, sel"
            String before = rest.substring(0, start).strip().replaceAll("[,;:\\-–(]+$", "").strip();
            int end = endOfSentence(rest, start);
            ingredients.add(before);
            statements.add(rest.substring(start, end).strip());
            rest = rest.substring(end);
        }
        ingredients.add(rest.strip());
        String ingredientsPart = String.join(" ", ingredients.stream().filter(s -> !s.isEmpty()).toList());
        return new Split(ingredientsPart, statements.isEmpty() ? null : String.join(" ", statements));
    }

    private static int firstPhrase(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int start = -1;
        for (String phrase : PHRASES) {
            int i = lower.indexOf(phrase);
            if (i >= 0 && (start < 0 || i < start)) {
                start = i;
            }
        }
        return start;
    }

    /** Just after the first ". " (or "." at the end) following start; the end of the text if none. */
    private static int endOfSentence(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            if (text.charAt(i) == '.' && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1)))) {
                return i + 1;
            }
        }
        return text.length();
    }
}
