package dev.labellens.reading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LabelReadingRepository extends JpaRepository<LabelReadingEntity, UUID> {
}
