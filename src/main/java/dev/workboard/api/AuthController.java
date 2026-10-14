package dev.workboard.api;

import dev.workboard.application.AuthService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirements
  public Views.UserResponse register(@Valid @RequestBody Views.Credentials body) {
    return Views.UserResponse.from(service.register(body.email(), body.password()));
  }

  @PostMapping("/token")
  @SecurityRequirements
  public Views.TokenResponse token(@Valid @RequestBody Views.Credentials body) {
    var session = service.login(body.email(), body.password());
    return new Views.TokenResponse(session.accessToken(), "Bearer", session.expiresIn());
  }

  @GetMapping("/me")
  public Views.UserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return Views.UserResponse.from(service.me(UUID.fromString(jwt.getSubject())));
  }
}
