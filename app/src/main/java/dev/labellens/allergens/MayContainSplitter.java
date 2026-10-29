package dev.labellens.allergens;

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
 */
public final class MayContainSplitter {

    /** Where the "may contain" statement starts. Lowercase; the text is lowercased before searching. */
    static final List<String> PHRASES = List.of(
            // French
            "peut contenir", "peut également contenir", "peut aussi contenir",
            "traces éventuelles", "traces possibles", "présence éventuelle", "traces de", "traces d'",
            "fabriqué dans un atelier", "produit dans un atelier", "élaboré dans un atelier",
            "fabriqué dans une usine", "produit dans une usine",
            // English
            "may contain", "may also contain", "traces of", "may be present",
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
        String lower = text.toLowerCase(Locale.ROOT);
        int start = -1;
        for (String phrase : PHRASES) {
            int i = lower.indexOf(phrase);
            if (i >= 0 && (start < 0 || i < start)) {
                start = i;
            }
        }
        if (start < 0) {
            return new Split(text.strip(), null);
        }
        String before = text.substring(0, start).strip();
        // "…, sel. Peut contenir…" → "…, sel." ; "…, sel, peut contenir…" → "…, sel"
        before = before.replaceAll("[,;:\\-–(]+$", "").strip();
        return new Split(before, text.substring(start).strip());
    }
}
