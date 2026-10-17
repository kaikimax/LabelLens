package dev.labellens.product;

import java.util.List;

/**
 * A page of results in a stable JSON format.
 * (Returning Spring's Page object directly gives a format Spring itself warns may change.)
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
