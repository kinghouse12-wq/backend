package dev.workboard.api;

import dev.workboard.application.TaskService;
import dev.workboard.domain.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/projects/{project}/tasks")
@Tag(name = "Tasks")
public class TaskController {
  private final TaskService service;

  public TaskController(TaskService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Views.TaskResponse create(
      @PathVariable UUID project,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody Views.TaskCreate body) {
    return Views.TaskResponse.from(
        service.create(
            project, UUID.fromString(jwt.getSubject()), body.title(), body.description()));
  }

  @GetMapping
  public Page<Views.TaskResponse> list(
      @PathVariable UUID project,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) TaskStatus status,
      @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service
        .list(project, UUID.fromString(jwt.getSubject()), status, page, size)
        .map(Views.TaskResponse::from);
  }

  @GetMapping("/{id}")
  public Views.TaskResponse get(
      @PathVariable UUID project,
      @PathVariable UUID id,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return Views.TaskResponse.from(service.get(project, id, UUID.fromString(jwt.getSubject())));
  }

  @PatchMapping("/{id}")
  public Views.TaskResponse update(
      @PathVariable UUID project,
      @PathVariable UUID id,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody Views.TaskPatch body) {
    var change =
        new TaskService.Change(
            body.expectedVersion(),
            body.title(),
            body.description(),
            body.status(),
            body.clearDescription());
    return Views.TaskResponse.from(
        service.update(project, id, UUID.fromString(jwt.getSubject()), change));
  }

  @GetMapping("/{id}/events")
  public Page<TaskEvent> events(
      @PathVariable UUID project,
      @PathVariable UUID id,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.events(project, id, UUID.fromString(jwt.getSubject()), page, size);
  }
}
