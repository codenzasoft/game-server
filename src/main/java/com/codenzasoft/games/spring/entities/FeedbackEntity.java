package com.codenzasoft.games.spring.entities;

import jakarta.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity(name = "FEEDBACK")
@Table(name = "feedback")
public class FeedbackEntity {

  public enum Type {
    BUG,
    FEEDBACK
  }

  public enum Status {
    NEW,
    REVIEWED,
    RESOLVED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private UserEntity user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private Type type;

  @Column(nullable = false, updatable = false)
  private String subject;

  @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.NEW;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Date createdAt;

  @PrePersist
  public void onCreate() {
    if (createdAt == null) {
      createdAt = new Date();
    }
  }

  public FeedbackEntity() {}

  public UUID getId() {
    return id;
  }

  public UserEntity getUser() {
    return user;
  }

  public void setUser(UserEntity user) {
    this.user = user;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Date getCreatedAt() {
    return createdAt;
  }
}
