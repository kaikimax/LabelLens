package dev.labellens.ai;

/** One image sent to the model: its bytes and its type ("image/jpeg" or "image/png"). */
public record ImageInput(byte[] data, String contentType) {
}
