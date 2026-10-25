package dev.labellens.reading;

import dev.labellens.ai.AiCallLog;
import dev.labellens.ai.AiCallRecord;
import dev.labellens.ai.ImageInput;
import dev.labellens.ai.ModelReply;
import dev.labellens.ai.VisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Reads a food label from photos:
 *   photos → model → JSON → PrintedLabel → checked → converted to per 100 g → sanity checks.
 *
 * If the model's answer is unusable (broken JSON, wrong values), it gets ONE more chance,
 * with the exact problem explained. Every attempt is recorded in the ai_call log.
 */
@Service
public class LabelReader {

    static final int MAX_ATTEMPTS = 2;
    static final String PURPOSE = "READ_LABEL";

    private final VisionModel model;
    private final JsonMapper json;
    private final AiCallLog callLog;
    private final String instructions;

    public LabelReader(VisionModel model,
                       JsonMapper json,
                       AiCallLog callLog,
                       @Value("classpath:prompts/read-label.md") Resource prompt) {
        this.model = model;
        this.json = json;
        this.callLog = callLog;
        this.instructions = load(prompt);
    }

    public ReadOutcome read(UUID productId, List<ImageInput> images) {
        String feedback = null;
        String lastRaw = null;
        String lastProblem = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String request = feedback == null ? instructions : instructions + "\n\n" + feedback;
            long start = System.nanoTime();

            ModelReply reply;
            try {
                reply = model.ask(request, images);
            } catch (RuntimeException e) {
                // Network error, wrong key, quota... Retrying the same request won't help.
                String reason = "AI call failed: " + describe(e);
                callLog.record(new AiCallRecord(productId, PURPOSE, model.name(), null, null,
                        millisSince(start), false, reason));
                return ReadOutcome.failed(reason, lastRaw, model.name());
            }

            lastRaw = reply.text();
            lastProblem = findProblem(reply.text());
            callLog.record(new AiCallRecord(productId, PURPOSE, reply.model(), reply.inputTokens(),
                    reply.outputTokens(), millisSince(start), lastProblem == null, lastProblem));

            if (lastProblem == null) {
                PrintedLabel printed = parse(reply.text());
                LabelReading reading = LabelConverter.toPer100g(printed);
                return ReadOutcome.extracted(reading, SanityChecks.check(reading), reply.text(), reply.model());
            }

            feedback = "IMPORTANT: your previous answer was rejected because: " + lastProblem
                    + ". Answer again with only the JSON object, exactly in the format described above.";
        }

        return ReadOutcome.failed(
                "Model answer still unusable after " + MAX_ATTEMPTS + " attempts: " + lastProblem,
                lastRaw, model.name());
    }

    /** Returns null if the answer is usable, otherwise a description of what's wrong. */
    private String findProblem(String text) {
        if (text == null || text.isBlank()) {
            return "the answer was empty";
        }
        PrintedLabel printed;
        try {
            printed = parse(text);
        } catch (JacksonException e) {
            return "the answer is not valid JSON in the expected format (" + firstLine(e.getMessage()) + ")";
        }
        if (printed == null) {
            return "the answer was JSON null";
        }
        List<String> problems = printed.problems();
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    private PrintedLabel parse(String text) {
        return json.readValue(stripCodeFences(text), PrintedLabel.class);
    }

    /** Models often wrap JSON in ```json ... ``` even when told not to. */
    static String stripCodeFences(String text) {
        String t = text.strip();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return t.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return t;
    }

    /**
     * The exception's message plus its causes: "Request failed" alone says nothing,
     * the cause underneath usually does (HTTP status, timeout, content filter...).
     */
    static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder(firstLine(e.getMessage()));
        Throwable cause = e.getCause();
        for (int depth = 0; cause != null && cause != e && depth < 5; depth++) {
            sb.append(" <- ").append(cause.getClass().getSimpleName()).append(": ").append(firstLine(cause.getMessage()));
            cause = cause.getCause();
        }
        String text = sb.toString();
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }

    /** Jackson error messages span several lines; the first one says what's wrong. */
    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String load(Resource prompt) {
        try {
            return prompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read prompt " + prompt, e);
        }
    }
}
