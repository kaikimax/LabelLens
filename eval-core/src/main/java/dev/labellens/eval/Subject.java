package dev.labellens.eval;

/**
 * The AI step being measured. The harness only knows "give it an input, get an attempt back".
 *
 * Implementations should not throw: a failed answer is a result to count, so it goes in
 * {@link Attempt#failed}. If one throws anyway, the runner records it as a failed attempt.
 */
@FunctionalInterface
public interface Subject<I, A> {

    Attempt<A> run(I input);
}
