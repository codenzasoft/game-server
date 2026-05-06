package com.codenzasoft.games.spring.entities;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.spring.config.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity(name = "USERS")
public class UserEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @Column(name = "user_name", nullable = false)
  String userName;

  @Column(name = "created_at", updatable = false, nullable = false)
  Date createdAt;

  @Column(name = "modified_at", nullable = false)
  Date modifiedAt;

  @Column(name = "email", unique = true)
  String email;

  @Column(name = "password")
  String password;

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", updatable = false)
  List<RoleEntity> roles = new ArrayList<>();

  @PrePersist
  public void onCreate() {
    final Date now = new Date();
    if (createdAt == null) {
      createdAt = now;
    }
    if (modifiedAt == null) {
      modifiedAt = now;
    }
  }

  @PreUpdate
  public void setModifiedAt() {
    this.modifiedAt = new Date();
  }

  public UserEntity() {}

  @JsonProperty("id")
  public UUID getId() {
    return id;
  }

  @JsonProperty("userName")
  public String getUsername() {
    return userName;
  }

  @JsonProperty("createdAt")
  public Date getCreatedAt() {
    return createdAt;
  }

  @JsonProperty("modifiedAt")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Date getModifiedAt() {
    return modifiedAt;
  }

  @JsonProperty("email")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getEmail() {
    return email;
  }

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  public String getPassword() {
    return password;
  }

  public void setUserName(final String userName) {
    this.userName = userName;
  }

  public void setEmail(final String email) {
    this.email = email;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  @JsonProperty("roles")
  public List<RoleEntity> getRoles() {
    return roles;
  }

  public Player toPlayer() {
    return new Player(getId(), getUsername());
  }

  public boolean isBot() {
    return getRoles().stream().anyMatch(role -> role.getRole().equals(Role.BOT));
  }
}
