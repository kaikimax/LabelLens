package dev.labellens.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data writes the SQL for save, findById, findAll(page)... at startup. */
public interface ProductRepository extends JpaRepository<Product, UUID> {
}
