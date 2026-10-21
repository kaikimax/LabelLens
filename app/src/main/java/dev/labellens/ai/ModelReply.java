package dev.labellens.ai;

/**
 * The model's answer, plus what it cost.
 * Token counts can be null if the provider doesn't report them.
 */
public record ModelReply(String text, String model, Integer inputTokens, Integer outputTokens) {
}
