package dev.workboard.infrastructure.persistence;

import dev.workboard.domain.TaskStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TaskJpaRepository extends JpaRepository<TaskEntity, UUID> {
  Optional<TaskEntity> findByIdAndProjectId(UUID id, UUID projectId);

  Page<TaskEntity> findByProjectId(UUID projectId, Pageable pageable);

  Page<TaskEntity> findByProjectIdAndStatus(UUID projectId, TaskStatus status, Pageable pageable);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
        update TaskEntity t set t.title=:title, t.description=:description, t.status=:status,
        t.updatedAt=:updatedAt, t.version=t.version+1
        where t.id=:id and t.projectId=:projectId and t.version=:expected
        """)
  int compareAndSet(
      @Param("id") UUID id,
      @Param("projectId") UUID projectId,
      @Param("title") String title,
      @Param("description") String description,
      @Param("status") TaskStatus status,
      @Param("updatedAt") Instant updatedAt,
      @Param("expected") long expected);
}
