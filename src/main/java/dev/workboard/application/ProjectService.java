package dev.workboard.application;

import dev.workboard.domain.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectService {
  private final Ports.Projects projects;
  private final Clock clock;

  public ProjectService(Ports.Projects projects, Clock clock) {
    this.projects = projects;
    this.clock = clock;
  }

  public Project get(UUID id, UUID owner) {
    return projects
        .ownedProject(id, owner)
        .orElseThrow(
            () -> new DomainException(DomainException.Code.NOT_FOUND, "Project not found."));
  }

  @Transactional
  public Project create(UUID owner, String name, String description) {
    Project project = new Project(UUID.randomUUID(), owner, name, description, clock.instant());
    projects.createProject(project);
    return project;
  }

  public Page<Project> list(UUID owner, int page, int size) {
    return projects.listProjects(owner, page, size);
  }

  @Transactional
  public void delete(UUID id, UUID owner) {
    get(id, owner);
    projects.deleteProject(id);
  }
}
