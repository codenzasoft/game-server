package com.codenzasoft.games.spring.entities.ids;

import com.codenzasoft.games.spring.config.Role;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.util.Objects;
import java.util.UUID;

public class RoleId {
  @Id
  @Column(name = "user_id", nullable = false)
  UUID userId;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "role", updatable = false)
  Role role;

  public RoleId() {}

  public RoleId(final UUID userId, final Role role) {
    this.userId = userId;
    this.role = role;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RoleId roleId = (RoleId) o;
    return Objects.equals(userId, roleId.userId) && role == roleId.role;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, role);
  }
}
