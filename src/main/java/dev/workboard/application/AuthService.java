package dev.workboard.application;

import dev.workboard.domain.DomainException;
import dev.workboard.domain.User;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {
  private final Ports.Users users;
  private final Ports.Passwords passwords;
  private final Ports.Tokens tokens;
  private final Clock clock;

  public AuthService(
      Ports.Users users, Ports.Passwords passwords, Ports.Tokens tokens, Clock clock) {
    this.users = users;
    this.passwords = passwords;
    this.tokens = tokens;
    this.clock = clock;
  }

  @Transactional
  public User register(String email, String password) {
    checkPasswordBytes(password);
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    if (users.userByEmail(normalized).isPresent()) {
      throw new DomainException(DomainException.Code.CONFLICT, "Email is already registered.");
    }
    User user = new User(UUID.randomUUID(), normalized, passwords.hash(password), clock.instant());
    users.createUser(user);
    return user;
  }

  public Ports.Session login(String email, String password) {
    checkPasswordBytes(password);
    User user = users.userByEmail(email.strip().toLowerCase(Locale.ROOT)).orElse(null);
    boolean valid = passwords.matches(password, user == null ? null : user.passwordHash());
    if (user == null || !valid) {
      throw new DomainException(
          DomainException.Code.INVALID_CREDENTIALS, "Invalid email or password.");
    }
    return tokens.issue(user.id());
  }

  public User me(UUID id) {
    return users
        .userById(id)
        .orElseThrow(
            () ->
                new DomainException(
                    DomainException.Code.INVALID_CREDENTIALS, "Invalid access token."));
  }

  private void checkPasswordBytes(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new DomainException(
          DomainException.Code.INVALID_REQUEST, "Password must not exceed 72 UTF-8 bytes.");
    }
  }
}
