package dev.workboard.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, UUID> {
  Optional<ProjectEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

  Page<ProjectEntity> findByOwnerId(UUID ownerId, Pageable pageable);
}
