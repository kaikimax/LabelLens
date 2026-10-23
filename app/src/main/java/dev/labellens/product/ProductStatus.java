package dev.labellens.product;

/** Where a product is in the processing chain. */
public enum ProductStatus {
    /** Photos saved, nothing processed yet (or processing crashed before finishing). */
    PENDING,
    /**
     * Label read, not scored. Only products uploaded during day 3 have this status:
     * since day 4, a successful reading always goes straight to DONE. Kept so old rows still load.
     */
    EXTRACTED,
    /** Label read and Nutri-Score computed (or deliberately not scored, e.g. drinks). */
    DONE,
    /** Something went wrong; the reason is stored with the product. */
    FAILED
}
