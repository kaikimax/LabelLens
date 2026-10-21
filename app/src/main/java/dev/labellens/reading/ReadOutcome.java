package dev.labellens.reading;

import java.util.List;

/**
 * Result of trying to read a label: either a reading (with possible warnings),
 * or a failure with a reason a human can understand.
 */
public record ReadOutcome(
        boolean success,
        LabelReading reading,
        List<String> warnings,
        String rawResponse,
        String model,
        String failureReason
) {

    static ReadOutcome extracted(LabelReading reading, List<String> warnings, String rawResponse, String model) {
        return new ReadOutcome(true, reading, List.copyOf(warnings), rawResponse, model, null);
    }

    static ReadOutcome failed(String reason, String rawResponse, String model) {
        return new ReadOutcome(false, null, List.of(), rawResponse, model, reason);
    }
}
