package dev.workboard.application;

import dev.workboard.domain.*;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {
  public record Change(
      long expectedVersion,
      String title,
      String description,
      TaskStatus status,
      boolean clearDescription) {}

  private final Ports.Tasks tasks;
  private final ProjectService projects;
  private final Clock clock;

  public TaskService(Ports.Tasks tasks, ProjectService projects, Clock clock) {
    this.tasks = tasks;
    this.projects = projects;
    this.clock = clock;
  }

  public Task get(UUID project, UUID task, UUID owner) {
    projects.get(project, owner);
    return tasks
        .taskInProject(task, project)
        .orElseThrow(() -> new DomainException(DomainException.Code.NOT_FOUND, "Task not found."));
  }

  @Transactional
  public Task create(UUID project, UUID owner, String title, String description) {
    projects.get(project, owner);
    Instant now = clock.instant();
    Task task =
        tasks.createTask(
            new Task(UUID.randomUUID(), project, title, description, TaskStatus.TODO, 0, now, now));
    tasks.recordEvent(
        new TaskEvent(
            UUID.randomUUID(), task.id(), owner, null, task.status(), task.version(), now));
    return task;
  }

  @Transactional
  public Task update(UUID project, UUID id, UUID owner, Change change) {
    Task current = get(project, id, owner);
    if (current.version() != change.expectedVersion()) throw conflict();
    TaskStatus target = change.status() == null ? current.status() : change.status();
    current.status().requireTransitionTo(target);
    String description =
        change.clearDescription()
            ? null
            : (change.description() == null ? current.description() : change.description());
    Task updated =
        new Task(
            current.id(),
            project,
            change.title() == null ? current.title() : change.title(),
            description,
            target,
            current.version() + 1,
            current.createdAt(),
            clock.instant());
    // The database checks and increments the version in a single UPDATE statement.
    if (!tasks.compareAndSetTask(updated, change.expectedVersion())) throw conflict();
    if (target != current.status()) {
      tasks.recordEvent(
          new TaskEvent(
              UUID.randomUUID(),
              id,
              owner,
              current.status(),
              target,
              updated.version(),
              updated.updatedAt()));
    }
    // Spring commits the task and audit writes together when this method returns.
    return updated;
  }

  public Page<Task> list(UUID project, UUID owner, TaskStatus status, int page, int size) {
    projects.get(project, owner);
    return tasks.listTasks(project, status, page, size);
  }

  public Page<TaskEvent> events(UUID project, UUID task, UUID owner, int page, int size) {
    get(project, task, owner);
    return tasks.listEvents(task, page, size);
  }

  private DomainException conflict() {
    return new DomainException(
        DomainException.Code.CONFLICT, "Task changed. Fetch the latest version and retry.");
  }
}
