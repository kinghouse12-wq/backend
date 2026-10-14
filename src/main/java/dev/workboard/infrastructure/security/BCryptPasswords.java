package dev.workboard.infrastructure.security;

import dev.workboard.application.Ports;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswords implements Ports.Passwords {
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  private final String dummy = encoder.encode(UUID.randomUUID().toString());

  public String hash(String password) {
    return encoder.encode(password);
  }

  public boolean matches(String password, String hash) {
    boolean valid = encoder.matches(password, hash == null ? dummy : hash);
    return hash != null && valid;
  }
}
