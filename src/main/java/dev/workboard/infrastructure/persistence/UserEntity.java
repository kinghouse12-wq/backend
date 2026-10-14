package dev.workboard.infrastructure.persistence;

import dev.workboard.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class UserEntity {
  @Id private UUID id;

  @Column(nullable = false, length = 320, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserEntity() {}

  public UserEntity(User user) {
    id = user.id();
    email = user.email();
    passwordHash = user.passwordHash();
    createdAt = user.createdAt();
  }

  public User domain() {
    return new User(id, email, passwordHash, createdAt);
  }
}
