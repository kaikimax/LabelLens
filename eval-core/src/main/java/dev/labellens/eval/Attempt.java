package dev.labellens.eval;

/**
 * What one run of the AI step produced: an answer, or the reason there's none,
 * plus what it cost (time and tokens).
 *
 * @param infraRetries how many times the call was retried because of the infrastructure
 *                     (rate limit, network), not because of the answer. Reported, never scored.
 */
public record Attempt<A>(A answer, String failure, long durationMs,
                         long inputTokens, long outputTokens, int infraRetries) {

    public static <A> Attempt<A> answered(A answer, long durationMs, long inputTokens, long outputTokens, int infraRetries) {
        return new Attempt<>(answer, null, durationMs, inputTokens, outputTokens, infraRetries);
    }

    public static <A> Attempt<A> failed(String failure, long durationMs, long inputTokens, long outputTokens, int infraRetries) {
        return new Attempt<>(null, failure == null ? "unknown failure" : failure,
                durationMs, inputTokens, outputTokens, infraRetries);
    }

    public boolean succeeded() {
        return failure == null;
    }
}
