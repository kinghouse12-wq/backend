package dev.workboard.application;

import dev.workboard.domain.*;
import java.util.Optional;
import java.util.UUID;

public final class Ports {
  private Ports() {}

  public interface Users {
    Optional<User> userByEmail(String email);

    Optional<User> userById(UUID id);

    void createUser(User user);
  }

  public interface Projects {
    Optional<Project> ownedProject(UUID id, UUID owner);

    Page<Project> listProjects(UUID owner, int page, int size);

    void createProject(Project project);

    void deleteProject(UUID id);
  }

  public interface Tasks {
    Optional<Task> taskInProject(UUID id, UUID project);

    Task createTask(Task task);

    boolean compareAndSetTask(Task task, long expectedVersion);

    Page<Task> listTasks(UUID project, TaskStatus status, int page, int size);

    void recordEvent(TaskEvent event);

    Page<TaskEvent> listEvents(UUID task, int page, int size);
  }

  public interface Passwords {
    String hash(String password);

    boolean matches(String password, String hash);
  }

  public interface Tokens {
    Session issue(UUID user);
  }

  public record Session(String accessToken, long expiresIn) {}
}
