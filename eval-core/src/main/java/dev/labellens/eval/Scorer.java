package dev.labellens.eval;

import java.util.List;

/**
 * Compares an answer with the answer key and returns one {@link Check} per part
 * (one per nutrition value, for example). It must return the same check names for every
 * case, so scores can be added up across cases.
 *
 * Only called for attempts that produced an answer: failed attempts are counted
 * by the runner (as wrong for every check).
 */
@FunctionalInterface
public interface Scorer<E, A> {

    List<Check> score(E expected, A answer);
}
