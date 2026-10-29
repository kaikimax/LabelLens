package dev.labellens.ai;

/**
 * A model that answers a text question (no images). Same idea as VisionModel: our code depends
 * on this small interface, tests use a fake, and only SpringAiTextModel knows about Spring AI.
 */
public interface TextModel {

    ModelReply ask(String instructions, String text);

    /** The deployment name, e.g. "gpt-4.1-nano". */
    String name();
}
