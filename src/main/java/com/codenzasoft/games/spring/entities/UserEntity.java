package com.codenzasoft.games.spring.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
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

  @Column(name = "modified_at", updatable = true, nullable = false)
  Date modifiedAt;

  @Column(name = "email", unique = true, nullable = true)
  String email;

  @Column(name = "password", nullable = true)
  String password;

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

  public void setPassword(final String password) {
    this.password = password;
  }
}
