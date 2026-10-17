package dev.labellens.product;

import java.util.UUID;

/** No product with this id. Becomes HTTP 404. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID id) {
        super("No product with id " + id);
    }
}
