package com.codenzasoft.games.spring.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity(name = "USERS")
public class UserEntity {
  @Id UUID id;

  @Column(name = "user_name", nullable = false)
  String userName;

  @Column(name = "created_at", updatable = false, nullable = false)
  Date createdAt;

  @Column(name = "modified_at")
  Date modifiedAt;

  @Column(name = "email", unique = true)
  String email;

  @Column(name = "password", nullable = true)
  String password;

  @PrePersist
  public void setCreatedAt() {
    if (email == null) {
      this.id = UUID.nameUUIDFromBytes(userName.getBytes());
    } else {
      this.id = UUID.nameUUIDFromBytes(email.getBytes());
    }
    this.createdAt = new Date();
  }

  @PreUpdate
  public void setModifiedAt() {
    this.modifiedAt = new Date();
  }

  public UserEntity() {}

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return userName;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public Date getModifiedAt() {
    return modifiedAt;
  }

  public String getEmail() {
    return email;
  }

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }
}
