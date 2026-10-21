package dev.labellens.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A pretend model for tests: answers with whatever replies you queue, in order.
 * When the queue is empty, it answers with a valid reading of the strawberry jam (p004).
 * No network, no cost, same answer every time.
 */
public class FakeVisionModel implements VisionModel {

    public static final String JAM_JSON = """
            {
              "name": "Confiture de Fraises",
              "brand": "EL BARAKA",
              "type": "GENERAL",
              "basis": "100g",
              "serving_size": null,
              "energy": 1088.4,
              "energy_unit": "kJ",
              "fat": 0.3,
              "saturated_fat": 0.0,
              "carbohydrates": 62.9,
              "sugars": 60.0,
              "fibre": null,
              "protein": 0.47,
              "salt": 0.05,
              "sodium": null,
              "sodium_unit": null,
              "ingredients_text": "Fraises, Sucre, gélifiant: Pectine de fruits et correcteur d'acidité: acide citrique.",
              "ingredients_language": "fr"
            }
            """;

    private final Deque<Object> queued = new ArrayDeque<>();
    private final List<String> receivedInstructions = new ArrayList<>();

    /** Queue text replies, used one per call. */
    public synchronized FakeVisionModel reply(String... texts) {
        for (String text : texts) {
            queued.add(text);
        }
        return this;
    }

    /** Make the next call throw, like a network error would. */
    public synchronized FakeVisionModel failWith(RuntimeException error) {
        queued.add(error);
        return this;
    }

    public synchronized void reset() {
        queued.clear();
        receivedInstructions.clear();
    }

    public synchronized List<String> receivedInstructions() {
        return List.copyOf(receivedInstructions);
    }

    @Override
    public synchronized ModelReply ask(String instructions, List<ImageInput> images) {
        receivedInstructions.add(instructions);
        Object next = queued.isEmpty() ? JAM_JSON : queued.poll();
        if (next instanceof RuntimeException error) {
            throw error;
        }
        return new ModelReply((String) next, name(), 1200, 300);
    }

    @Override
    public String name() {
        return "fake-model";
    }
}
