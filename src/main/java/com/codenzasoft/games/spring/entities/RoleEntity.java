package com.codenzasoft.games.spring.entities;

import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.ids.RoleId;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity(name = "USER_ROLES")
@IdClass(RoleId.class)
public class RoleEntity {
  @Id
  @Column(name = "user_id")
  UUID userId;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "role", updatable = false)
  Role role;

  @Column(name = "created_at", updatable = false, nullable = false)
  Date createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = new Date();
    }
  }

  public RoleEntity() {}

  public RoleEntity(final UUID userId, final Role role) {
    this.userId = userId;
    this.role = role;
  }

  @JsonProperty("userId")
  public UUID getUserId() {
    return userId;
  }

  @JsonProperty("role")
  public Role getRole() {
    return role;
  }

  @JsonProperty("createdAt")
  public Date getCreatedAt() {
    return createdAt;
  }
}
