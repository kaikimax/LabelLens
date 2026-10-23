package dev.labellens.nutriscore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NutriScoreRepository extends JpaRepository<NutriScoreEntity, UUID> {
}
