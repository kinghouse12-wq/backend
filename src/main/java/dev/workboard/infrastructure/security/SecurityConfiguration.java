package dev.workboard.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.workboard.infrastructure.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  private SecretKey key(AppProperties props) {
    return new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(AppProperties props) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key(props)));
  }

  @Bean
  JwtDecoder jwtDecoder(AppProperties props, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key(props)).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> claims =
        jwt -> {
          boolean valid =
              jwt.getExpiresAt() != null
                  && jwt.getIssuedAt() != null
                  && jwt.getSubject() != null
                  && jwt.getAudience() != null
                  && jwt.getAudience().contains(props.audience())
                  && !jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(30))
                  && jwt.getExpiresAt().isAfter(jwt.getIssuedAt());
          try {
            if (valid) UUID.fromString(jwt.getSubject());
          } catch (IllegalArgumentException ex) {
            valid = false;
          }
          return valid
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Required token claims are invalid.", null));
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ofSeconds(30)),
            new JwtIssuerValidator(props.issuer()),
            claims));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/token",
                        "/actuator/health/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(
                        (request, response, error) -> {
                          response.setStatus(401);
                          response.setHeader("WWW-Authenticate", "Bearer");
                          response.setContentType("application/problem+json");
                          response
                              .getWriter()
                              .write(
                                  "{\"status\":401,\"title\":\"Unauthorized\","
                                      + "\"detail\":\"A valid bearer access token is required.\",\"code\":\"INVALID_CREDENTIALS\","
                                      + "\"requestId\":\""
                                      + request.getAttribute("requestId")
                                      + "\"}");
                        }))
        .build();
  }

  @Bean
  CorsConfigurationSource cors(AppProperties props) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(props.corsOrigins() == null ? List.of() : props.corsOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
    configuration.setExposedHeaders(List.of("X-Request-ID"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
