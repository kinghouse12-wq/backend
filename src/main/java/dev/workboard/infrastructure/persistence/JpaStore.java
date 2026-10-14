package dev.workboard.infrastructure.persistence;

import dev.workboard.application.Ports;
import dev.workboard.domain.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class JpaStore implements Ports.Users, Ports.Projects, Ports.Tasks {
  private final UserJpaRepository users;
  private final ProjectJpaRepository projects;
  private final TaskJpaRepository tasks;
  private final TaskEventJpaRepository events;

  public JpaStore(
      UserJpaRepository users,
      ProjectJpaRepository projects,
      TaskJpaRepository tasks,
      TaskEventJpaRepository events) {
    this.users = users;
    this.projects = projects;
    this.tasks = tasks;
    this.events = events;
  }

  public Optional<User> userByEmail(String email) {
    return users.findByEmail(email).map(UserEntity::domain);
  }

  public Optional<User> userById(UUID id) {
    return users.findById(id).map(UserEntity::domain);
  }

  public void createUser(User user) {
    users.saveAndFlush(new UserEntity(user));
  }

  public Optional<Project> ownedProject(UUID id, UUID owner) {
    return projects.findByIdAndOwnerId(id, owner).map(ProjectEntity::domain);
  }

  public void createProject(Project project) {
    projects.saveAndFlush(new ProjectEntity(project));
  }

  public void deleteProject(UUID id) {
    projects.deleteById(id);
    projects.flush();
  }

  public Page<Project> listProjects(UUID owner, int page, int size) {
    var result = projects.findByOwnerId(owner, newest(page, size));
    return new Page<>(
        result.map(ProjectEntity::domain).getContent(), result.getTotalElements(), page, size);
  }

  public Optional<Task> taskInProject(UUID id, UUID project) {
    return tasks.findByIdAndProjectId(id, project).map(TaskEntity::domain);
  }

  public Task createTask(Task task) {
    return tasks.saveAndFlush(new TaskEntity(task)).domain();
  }

  public boolean compareAndSetTask(Task task, long expectedVersion) {
    return tasks.compareAndSet(
            task.id(),
            task.projectId(),
            task.title(),
            task.description(),
            task.status(),
            task.updatedAt(),
            expectedVersion)
        == 1;
  }

  public Page<Task> listTasks(UUID project, TaskStatus status, int page, int size) {
    var result =
        status == null
            ? tasks.findByProjectId(project, newest(page, size))
            : tasks.findByProjectIdAndStatus(project, status, newest(page, size));
    return new Page<>(
        result.map(TaskEntity::domain).getContent(), result.getTotalElements(), page, size);
  }

  public void recordEvent(TaskEvent event) {
    events.saveAndFlush(new TaskEventEntity(event));
  }

  public Page<TaskEvent> listEvents(UUID task, int page, int size) {
    var result = events.findByTaskId(task, PageRequest.of(page, size, Sort.by("version")));
    return new Page<>(
        result.map(TaskEventEntity::domain).getContent(), result.getTotalElements(), page, size);
  }

  private PageRequest newest(int page, int size) {
    return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
  }
}
