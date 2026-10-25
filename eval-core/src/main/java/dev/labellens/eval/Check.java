package dev.labellens.eval;

/**
 * The result of comparing ONE part of an answer with the answer key, e.g. "salt".
 * Expected and actual are kept as text so the report can show every disagreement.
 *
 * @param note optional explanation, e.g. which misread values changed a grade
 */
public record Check(String name, boolean passed, String expected, String actual, String note) {

    public static Check of(String name, boolean passed, Object expected, Object actual) {
        return new Check(name, passed, String.valueOf(expected), String.valueOf(actual), null);
    }

    public Check withNote(String note) {
        return new Check(name, passed, expected, actual, note);
    }
}
