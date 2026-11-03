package dev.labellens.allergens;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AllergenResultRepository extends JpaRepository<AllergenResultEntity, UUID> {
}
