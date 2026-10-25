package dev.labellens.eval;

import java.util.List;
import java.util.Set;

/**
 * One run of one case: the attempt's cost and outcome, and its checks.
 *
 * @param run    1, 2, 3... (each case runs several times)
 * @param checks empty when the attempt failed
 */
public record CaseRun(String caseId, Set<String> tags, int run, String failure,
                      long durationMs, long inputTokens, long outputTokens, int infraRetries,
                      List<Check> checks) {

    public boolean succeeded() {
        return failure == null;
    }
}
