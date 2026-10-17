package dev.labellens.product;

/**
 * Where a product is in the processing chain.
 * Day 2: every upload stays PENDING. Later days move it forward.
 */
public enum ProductStatus {
    /** Photos saved, nothing processed yet. */
    PENDING,
    /** The AI has read the label (day 3). */
    EXTRACTED,
    /** Nutri-Score and allergens done (days 4–6). */
    DONE,
    /** Something went wrong; the reason is stored with the product. */
    FAILED
}
