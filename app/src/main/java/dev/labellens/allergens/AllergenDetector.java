package dev.labellens.allergens;

import dev.labellens.ai.AiCallLog;
import dev.labellens.ai.AiCallRecord;
import dev.labellens.ai.TextModel;
import dev.labellens.reading.LabelReader;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The allergen setup the app uses (decisions log, 2026-10-31): word list + gpt-4.1, the same steps
 * as the eval's "combined" setup (AllergenSetups), so the eval scores describe what the app does.
 *
 *   1. MayContainSplitter on the ingredients text, in case the reader left a "may contain" sentence in;
 *   2. contains   = word list ∪ AI, on the ingredients part;
 *   3. mayContain = word list, on the printed "may contain" sentence(s).
 *
 * If the AI fails, the product is NOT failed: the word list result is kept and the outcome says
 * the AI didn't check it (aiProblem). The word list alone caught 96.7% on the French test set;
 * an error page would give the user nothing.
 */
@Service
public class AllergenDetector {

    static final String PURPOSE = "FIND_ALLERGENS";

    private final AiAllergenFinder finder;
    private final TextModel model;
    private final AiCallLog callLog;

    public AllergenDetector(AiAllergenFinder finder, TextModel allergenModel, AiCallLog callLog) {
        this.finder = finder;
        this.model = allergenModel;
        this.callLog = callLog;
    }

    public AllergenOutcome detect(UUID productId, String ingredientsText, String mayContainText) {
        MayContainSplitter.Split split = MayContainSplitter.split(ingredientsText);
        Set<String> mayContain = AllergenWordList.find(join(split.mayContain(), mayContainText));
        String ingredients = split.ingredients();

        if (ingredients == null || ingredients.isBlank()) {
            return new AllergenOutcome(false, Set.of(), mayContain, Set.of(), null, null, null);
        }

        Set<String> byWordList = AllergenWordList.find(ingredients);
        long start = System.nanoTime();
        AiAllergenFinder.Result result;
        try {
            result = finder.find(model, ingredients);
        } catch (RuntimeException e) {
            String reason = "AI call failed: " + LabelReader.describe(e);
            callLog.record(new AiCallRecord(productId, PURPOSE, model.name(), null, null,
                    millisSince(start), false, reason));
            return new AllergenOutcome(true, byWordList, mayContain, byWordList, null, model.name(), reason);
        }

        var reply = result.reply();
        callLog.record(new AiCallRecord(productId, PURPOSE, reply.model(), reply.inputTokens(),
                reply.outputTokens(), millisSince(start), result.ok(), result.problem()));
        if (!result.ok()) {
            return new AllergenOutcome(true, byWordList, mayContain, byWordList, null, reply.model(),
                    "Unusable AI answer: " + result.problem());
        }

        Set<String> contains = new TreeSet<>(byWordList);
        contains.addAll(result.allergens());
        return new AllergenOutcome(true, Set.copyOf(contains), mayContain, byWordList,
                Set.copyOf(result.allergens()), reply.model(), null);
    }

    /** The statement the splitter found and the one the reader copied; usually only one exists. */
    private static String join(String a, String b) {
        boolean hasA = a != null && !a.isBlank();
        boolean hasB = b != null && !b.isBlank();
        if (hasA && hasB) {
            return a + " " + b;
        }
        return hasA ? a : hasB ? b : "";
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
