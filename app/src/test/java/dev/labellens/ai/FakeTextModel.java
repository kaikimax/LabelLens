package dev.labellens.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A pretend text model for tests, like FakeVisionModel: answers with the queued replies in order,
 * then with {"allergens": []}. No network, no cost.
 */
public class FakeTextModel implements TextModel {

    public static final String NO_ALLERGENS = "{\"allergens\": []}";

    private final Deque<Object> queued = new ArrayDeque<>();
    private final List<String> receivedTexts = new ArrayList<>();

    public synchronized FakeTextModel reply(String... texts) {
        for (String text : texts) {
            queued.add(text);
        }
        return this;
    }

    public synchronized FakeTextModel failWith(RuntimeException error) {
        queued.add(error);
        return this;
    }

    public synchronized void reset() {
        queued.clear();
        receivedTexts.clear();
    }

    /** What the model was asked about: the ingredients part only, never the "may contain" sentence. */
    public synchronized List<String> receivedTexts() {
        return List.copyOf(receivedTexts);
    }

    @Override
    public synchronized ModelReply ask(String instructions, String text) {
        receivedTexts.add(text);
        Object next = queued.isEmpty() ? NO_ALLERGENS : queued.poll();
        if (next instanceof RuntimeException error) {
            throw error;
        }
        return new ModelReply((String) next, name(), 200, 12);
    }

    @Override
    public String name() {
        return "fake-text-model";
    }
}
