package dev.labellens.eval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runs every case several times through the AI step and scores each answer.
 *
 * Runs are sequential on purpose: model deployments have a tokens-per-minute limit,
 * and an eval that trips it measures the rate limit instead of the model.
 */
public final class EvalRunner {

    private final Consumer<String> progress;

    /** @param progress receives one line per run, e.g. to print to the console */
    public EvalRunner(Consumer<String> progress) {
        this.progress = progress == null ? line -> { } : progress;
    }

    public <I, E, A> EvalResult run(String suite, List<EvalCase<I, E>> cases,
                                    Subject<I, A> subject, Scorer<E, A> scorer, int runsPerCase) {
        if (runsPerCase < 1) {
            throw new IllegalArgumentException("runsPerCase must be at least 1, got " + runsPerCase);
        }
        Instant started = Instant.now();
        List<CaseRun> runs = new ArrayList<>();
        int total = cases.size() * runsPerCase;
        int done = 0;

        for (EvalCase<I, E> c : cases) {
            for (int run = 1; run <= runsPerCase; run++) {
                Attempt<A> attempt = attempt(subject, c.input());
                List<Check> checks = attempt.succeeded()
                        ? List.copyOf(scorer.score(c.expected(), attempt.answer()))
                        : List.of();
                runs.add(new CaseRun(c.id(), c.tags(), run, attempt.failure(), attempt.durationMs(),
                        attempt.inputTokens(), attempt.outputTokens(), attempt.infraRetries(), checks));

                done++;
                long passed = checks.stream().filter(Check::passed).count();
                progress.accept(String.format("[%d/%d] %s run %d: %s", done, total, c.id(), run,
                        attempt.succeeded() ? passed + "/" + checks.size() + " checks passed"
                                : "FAILED (" + attempt.failure() + ")"));
            }
        }
        return new EvalResult(suite, runsPerCase, started, Instant.now(), runs);
    }

    private static <I, A> Attempt<A> attempt(Subject<I, A> subject, I input) {
        long start = System.nanoTime();
        try {
            Attempt<A> a = subject.run(input);
            return a != null ? a : Attempt.failed("the subject returned nothing", millisSince(start), 0, 0, 0);
        } catch (RuntimeException e) {
            return Attempt.failed("exception: " + e, millisSince(start), 0, 0, 0);
        }
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
