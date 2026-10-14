package dev.workboard.api;

import dev.workboard.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class Views {
  private Views() {}

  public record Credentials(
      @NotBlank @Email @Size(max = 320) String email,
      @NotNull @Size(min = 12, max = 72) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
          String password) {}

  public record UserResponse(UUID id, String email, Instant createdAt) {
    public static UserResponse from(User user) {
      return new UserResponse(user.id(), user.email(), user.createdAt());
    }
  }

  public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

  public record ProjectCreate(
      @NotBlank @Size(max = 100) String name, @Size(max = 2000) String description) {
    public ProjectCreate {
      if (name != null) name = name.strip();
    }
  }

  public record ProjectResponse(UUID id, String name, String description, Instant createdAt) {
    public static ProjectResponse from(Project p) {
      return new ProjectResponse(p.id(), p.name(), p.description(), p.createdAt());
    }
  }

  public record TaskCreate(
      @NotBlank @Size(max = 200) String title, @Size(max = 4000) String description) {
    public TaskCreate {
      if (title != null) title = title.strip();
    }
  }

  public record TaskPatch(
      @NotNull @PositiveOrZero Long expectedVersion,
      @Size(min = 1, max = 200) String title,
      @Size(max = 4000) String description,
      TaskStatus status,
      Boolean clearDescription) {
    public TaskPatch {
      clearDescription = Boolean.TRUE.equals(clearDescription);
      if (title != null) title = title.strip();
    }

    @AssertTrue(
        message = "Supply a field to change; description and clearDescription cannot both be set.")
    @Schema(hidden = true)
    public boolean isMeaningful() {
      return (title != null || description != null || status != null || clearDescription)
          && !(clearDescription && description != null);
    }
  }

  public record TaskResponse(
      UUID id,
      UUID projectId,
      String title,
      String description,
      TaskStatus status,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    public static TaskResponse from(Task t) {
      return new TaskResponse(
          t.id(),
          t.projectId(),
          t.title(),
          t.description(),
          t.status(),
          t.version(),
          t.createdAt(),
          t.updatedAt());
    }
  }
}
