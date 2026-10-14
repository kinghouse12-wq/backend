package dev.workboard.infrastructure.persistence;

import dev.workboard.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_events")
public class TaskEventEntity {
  @Id private UUID id;

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 20)
  private TaskStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 20)
  private TaskStatus toStatus;

  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected TaskEventEntity() {}

  public TaskEventEntity(TaskEvent event) {
    id = event.id();
    taskId = event.taskId();
    actorId = event.actorId();
    fromStatus = event.fromStatus();
    toStatus = event.toStatus();
    version = event.version();
    createdAt = event.createdAt();
  }

  public TaskEvent domain() {
    return new TaskEvent(id, taskId, actorId, fromStatus, toStatus, version, createdAt);
  }
}
