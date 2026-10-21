package dev.labellens.ai;

import java.util.List;

/**
 * An AI model that can look at images and answer in text.
 *
 * Our code depends on this small interface, not on Spring AI directly:
 * - tests use a fake implementation (no cost, no network, same answer every time);
 * - if Spring AI changes again, only SpringAiVisionModel needs updating.
 */
public interface VisionModel {

    ModelReply ask(String instructions, List<ImageInput> images);

    /** Which model/deployment this is, for the ai_call log. */
    String name();
}
