package dev.labellens.foodeval;

import dev.labellens.allergens.AiAllergenFinder;
import dev.labellens.allergens.AllergenWordList;
import dev.labellens.allergens.MayContainSplitter;
import dev.labellens.ai.TextModel;
import dev.labellens.eval.Attempt;
import dev.labellens.eval.Subject;

import java.util.Set;
import java.util.TreeSet;

/**
 * The three setups compared in Test 3 (decisions log, 2026-10-26). All of them:
 *   1. split off the "may contain" statement (MayContainSplitter, Java);
 *   2. find the allergens in the ingredients part: word list, AI, or both;
 *   3. find the "may contain" allergens with the word list only (printed, never guessed).
 *
 * Setup names are file-name safe (they become report names): "wordlist", "ai-gpt-4.1-nano",
 * "combined-gpt-4.1-nano".
 */
public final class AllergenSetups {

    private AllergenSetups() {
    }

    /** False until AllergenWordList is written: setups that need it are then skipped. */
    public static boolean wordListAvailable() {
        try {
            AllergenWordList.find("lait");
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    public static Subject<String, AllergenAnswer> wordList() {
        return text -> {
            long start = System.nanoTime();
            var split = MayContainSplitter.split(text);
            AllergenAnswer answer = new AllergenAnswer(
                    AllergenWordList.find(split.ingredients()),
                    mayContainByWordList(split, true));
            return Attempt.answered(answer, millisSince(start), 0, 0, 0);
        };
    }

    public static Subject<String, AllergenAnswer> ai(AiAllergenFinder finder, TextModel model, boolean wordListReady) {
        return text -> run(finder, model, text, false, wordListReady);
    }

    public static Subject<String, AllergenAnswer> combined(AiAllergenFinder finder, TextModel model) {
        return text -> run(finder, model, text, true, true);
    }

    private static Attempt<AllergenAnswer> run(AiAllergenFinder finder, TextModel model, String text,
                                               boolean addWordList, boolean wordListReady) {
        long start = System.nanoTime();
        var split = MayContainSplitter.split(text);
        var result = finder.find(model, split.ingredients());
        long in = result.reply().inputTokens() == null ? 0 : result.reply().inputTokens();
        long out = result.reply().outputTokens() == null ? 0 : result.reply().outputTokens();
        if (!result.ok()) {
            return Attempt.failed(result.problem() + " | answer: " + abbreviate(result.reply().text()),
                    millisSince(start), in, out, 0);
        }
        Set<String> allergens = new TreeSet<>(result.allergens());
        if (addWordList) {
            allergens.addAll(AllergenWordList.find(split.ingredients()));
        }
        return Attempt.answered(new AllergenAnswer(allergens, mayContainByWordList(split, wordListReady)),
                millisSince(start), in, out, 0);
    }

    /** Null (= not computed, skipped by the scorer) while the word list isn't written. */
    private static Set<String> mayContainByWordList(MayContainSplitter.Split split, boolean wordListReady) {
        if (!wordListReady) {
            return null;
        }
        return split.mayContain() == null ? Set.of() : AllergenWordList.find(split.mayContain());
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "(none)";
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() > 200 ? flat.substring(0, 200) + "..." : flat;
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
