package dev.labellens.costs;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * List prices in US$ per million tokens, Azure OpenAI Global Standard. Check the Azure pricing page
 * when they change: the cost report is an estimate from token counts, not an Azure invoice.
 *
 * Azure reports versioned names ("gpt-4.1-mini-2025-04-14"), so a model is matched by its LONGEST
 * known prefix: "gpt-4.1-mini-..." must not get the "gpt-4.1" price (5x higher).
 */
public final class ModelPrices {

    public record Price(double inputPerMillion, double outputPerMillion) {

        public double cost(long inputTokens, long outputTokens) {
            return (inputTokens * inputPerMillion + outputTokens * outputPerMillion) / 1_000_000;
        }
    }

    static final Map<String, Price> LIST_PRICES = Map.of(
            "gpt-4.1", new Price(2.00, 8.00),
            "gpt-4.1-mini", new Price(0.40, 1.60),
            "gpt-4.1-nano", new Price(0.10, 0.40));

    private ModelPrices() {
    }

    /**
     * Empty for an unknown model (the report then says so instead of counting it as free).
     * A fine-tuned model ("gpt-4.1-nano-2025-04-14.ft-...") gets its base price: fine-tuned inference
     * may cost more, and the hosting fee isn't per token (day 7 notes).
     */
    public static Optional<Price> of(String model) {
        if (model == null) {
            return Optional.empty();
        }
        return LIST_PRICES.keySet().stream()
                .filter(prefix -> model.equals(prefix) || model.startsWith(prefix + "-") || model.startsWith(prefix + "."))
                .max(Comparator.comparingInt(String::length))
                .map(LIST_PRICES::get);
    }
}
