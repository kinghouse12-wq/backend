package dev.workboard.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskEventJpaRepository extends JpaRepository<TaskEventEntity, UUID> {
  Page<TaskEventEntity> findByTaskId(UUID taskId, Pageable pageable);
}
