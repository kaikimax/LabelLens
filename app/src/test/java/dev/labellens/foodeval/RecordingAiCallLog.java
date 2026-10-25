package dev.labellens.foodeval;

import dev.labellens.ai.AiCallLog;
import dev.labellens.ai.AiCallRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the AI calls in memory instead of the database, so the eval can add up the
 * tokens of each run. Runs are sequential, so a plain list is enough.
 */
public class RecordingAiCallLog implements AiCallLog {

    private final List<AiCallRecord> calls = new ArrayList<>();

    @Override
    public synchronized void record(AiCallRecord call) {
        calls.add(call);
    }

    public synchronized int size() {
        return calls.size();
    }

    /** The calls recorded since position {@code from}. */
    public synchronized List<AiCallRecord> since(int from) {
        return List.copyOf(calls.subList(from, calls.size()));
    }
}
