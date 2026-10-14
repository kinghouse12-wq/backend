package dev.workboard.infrastructure.security;

import dev.workboard.application.Ports;
import dev.workboard.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens implements Ports.Tokens {
  private final JwtEncoder encoder;
  private final AppProperties props;
  private final Clock clock;

  public JwtTokens(JwtEncoder encoder, AppProperties props, Clock clock) {
    this.encoder = encoder;
    this.props = props;
    this.clock = clock;
  }

  public Ports.Session issue(UUID user) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(user.toString())
            .issuer(props.issuer())
            .audience(List.of(props.audience()))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(props.tokenSeconds()))
            .build();
    String value =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new Ports.Session(value, props.tokenSeconds());
  }
}
