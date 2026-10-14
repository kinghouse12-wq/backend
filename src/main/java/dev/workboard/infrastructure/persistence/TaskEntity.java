package dev.workboard.infrastructure.persistence;

import dev.workboard.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class TaskEntity {
  @Id private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 4000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TaskStatus status;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TaskEntity() {}

  public TaskEntity(Task task) {
    id = task.id();
    projectId = task.projectId();
    title = task.title();
    description = task.description();
    status = task.status();
    createdAt = task.createdAt();
    updatedAt = task.updatedAt();
  }

  public Task domain() {
    return new Task(
        id,
        projectId,
        title,
        description,
        status,
        version == null ? 0 : version,
        createdAt,
        updatedAt);
  }
}
