package dev.workboard.infrastructure.config;

import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
    @NotBlank @Size(min = 32) String jwtSecret,
    @NotBlank String issuer,
    @NotBlank String audience,
    @Min(60) @Max(3600) long tokenSeconds,
    List<String> corsOrigins) {
  @AssertTrue(message = "JWT_SECRET must not be a placeholder")
  public boolean isSecretConfigured() {
    return jwtSecret != null && !jwtSecret.contains("replace-me");
  }
}
