package dev.labellens.product;

/**
 * The site has had its daily number of uploads (decisions log, 2026-11-05). Every upload costs
 * AI calls on the owner's Azure credit, and the live site is public.
 */
public class UploadLimitException extends RuntimeException {

    public UploadLimitException(int limit) {
        super("The demo has reached its limit of " + limit + " uploads in 24 hours. Please try again tomorrow.");
    }
}
