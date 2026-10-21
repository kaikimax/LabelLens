package dev.labellens.ai;

import java.util.UUID;

/**
 * One call to a model, as it will be stored in the ai_call table.
 * "success" means the answer was usable, not just that the HTTP call worked.
 */
public record AiCallRecord(
        UUID productId,
        String purpose,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        long durationMs,
        boolean success,
        String error
) {
}
