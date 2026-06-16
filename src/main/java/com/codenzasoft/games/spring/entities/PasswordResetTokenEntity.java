package com.codenzasoft.games.spring.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenEntity {

  @Id
  @Column(name = "token", nullable = false)
  private UUID token;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  public PasswordResetTokenEntity() {}

  public PasswordResetTokenEntity(UUID token, UUID userId, Instant expiresAt) {
    this.token = token;
    this.userId = userId;
    this.expiresAt = expiresAt;
  }

  public UUID getToken() {
    return token;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
