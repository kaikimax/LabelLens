package dev.labellens.eval;

/**
 * How similar two texts are, from 0 (nothing in common) to 1 (identical),
 * based on the edit distance: the number of single-character insertions, deletions
 * or substitutions needed to turn one text into the other.
 *
 * Example: "sucre, sel" vs "sucre, sell" → 1 edit over 11 characters → about 0.91.
 *
 * A tool, not a rule: deciding what similarity counts as "correct" belongs to the
 * comparison rule of each eval.
 */
public final class TextSimilarity {

    private TextSimilarity() {
    }

    public static double similarity(String a, String b) {
        if (a == null || b == null) {
            return a == b ? 1.0 : 0.0;
        }
        int longest = Math.max(a.length(), b.length());
        if (longest == 0) {
            return 1.0;
        }
        return 1.0 - (double) editDistance(a, b) / longest;
    }

    /** Levenshtein distance, computed with two rows of a table instead of the whole table. */
    public static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
