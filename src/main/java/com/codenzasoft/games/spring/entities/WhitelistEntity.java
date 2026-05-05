package com.codenzasoft.games.spring.entities;

import com.codenzasoft.games.spring.config.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.Date;

/**
 * A whitelist entry granting a specific email permission to register an account. The {@code role}
 * column is the role the new user will be assigned at registration. Emails are stored lowercased.
 */
@Entity(name = "WHITELIST")
@Table(name = "whitelist")
public class WhitelistEntity {
  @Id
  @Column(name = "email", nullable = false)
  String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  Role role;

  @Column(name = "created_at", updatable = false, nullable = false)
  Date createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = new Date();
    }
  }

  public WhitelistEntity() {}

  public WhitelistEntity(final String email, final Role role) {
    this.email = email == null ? null : email.toLowerCase();
    this.role = role;
  }

  @JsonProperty("email")
  public String getEmail() {
    return email;
  }

  @JsonProperty("role")
  public Role getRole() {
    return role;
  }

  @JsonProperty("createdAt")
  public Date getCreatedAt() {
    return createdAt;
  }

  public void setEmail(final String email) {
    this.email = email == null ? null : email.toLowerCase();
  }

  public void setRole(final Role role) {
    this.role = role;
  }
}
