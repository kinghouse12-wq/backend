package dev.workboard.api;

import dev.workboard.application.ProjectService;
import dev.workboard.domain.Page;
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
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects")
public class ProjectController {
  private final ProjectService service;

  public ProjectController(ProjectService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Views.ProjectResponse create(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody Views.ProjectCreate body) {
    return Views.ProjectResponse.from(
        service.create(UUID.fromString(jwt.getSubject()), body.name(), body.description()));
  }

  @GetMapping
  public Page<Views.ProjectResponse> list(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service
        .list(UUID.fromString(jwt.getSubject()), page, size)
        .map(Views.ProjectResponse::from);
  }

  @GetMapping("/{id}")
  public Views.ProjectResponse get(
      @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return Views.ProjectResponse.from(service.get(id, UUID.fromString(jwt.getSubject())));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    service.delete(id, UUID.fromString(jwt.getSubject()));
  }
}
