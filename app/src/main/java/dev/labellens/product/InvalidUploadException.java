package dev.labellens.product;

/** The user sent photos we can't accept (wrong count, wrong type, empty file). Becomes HTTP 400. */
public class InvalidUploadException extends RuntimeException {

    public InvalidUploadException(String message) {
        super(message);
    }
}
