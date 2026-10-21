package dev.labellens.ai;

/**
 * Where AI calls get recorded. An interface so unit tests can collect calls in a list
 * instead of needing a database.
 */
@FunctionalInterface
public interface AiCallLog {

    void record(AiCallRecord call);
}
