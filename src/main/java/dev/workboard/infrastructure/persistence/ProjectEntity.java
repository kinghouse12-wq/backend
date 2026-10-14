package dev.workboard.infrastructure.persistence;

import dev.workboard.domain.Project;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectEntity {
  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 2000)
  private String description;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ProjectEntity() {}

  public ProjectEntity(Project project) {
    id = project.id();
    ownerId = project.ownerId();
    name = project.name();
    description = project.description();
    createdAt = project.createdAt();
  }

  public Project domain() {
    return new Project(id, ownerId, name, description, createdAt);
  }
}
