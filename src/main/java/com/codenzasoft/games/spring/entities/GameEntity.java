package com.codenzasoft.games.spring.entities;

import com.codenzasoft.games.common.GameType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity(name = "GAMES")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "game_type", discriminatorType = DiscriminatorType.STRING)
public abstract class GameEntity {
  @Id UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "game_type", insertable = false, updatable = false)
  GameType gameType;

  @Column(name = "created_at", updatable = false, nullable = false)
  Date createdAt;

  @Column(name = "over_at")
  Date overAt;

  @Column(name = "quit_at")
  Date quitAt;

  @OneToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "game_players",
      joinColumns = @JoinColumn(name = "game_id", referencedColumnName = "id"),
      inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))
  @OrderColumn(name = "player_order")
  List<UserEntity> users = new ArrayList<>();

  @PrePersist
  public void setCreatedAt() {
    this.createdAt = new Date();
  }

  public GameEntity() {}

  public GameEntity(final UUID id, final GameType gameType) {
    this.id = id;
    this.gameType = gameType;
  }

  @JsonProperty("id")
  public UUID getId() {
    return id;
  }

  @JsonProperty("gameType")
  public GameType getGameType() {
    return gameType;
  }

  @JsonProperty("createdAt")
  public Date getCreatedAt() {
    return createdAt;
  }

  @JsonProperty("overAt")
  public Date getOverAt() {
    return overAt;
  }

  public void setOverAt(final Date overAt) {
    this.overAt = overAt;
  }

  @JsonIgnore
  public boolean isOver() {
    return overAt != null;
  }

  @JsonProperty("quitAt")
  public Date getQuitAt() {
    return quitAt;
  }

  public void setQuitAt(final Date quitAt) {
    this.quitAt = quitAt;
  }

  @JsonIgnore
  public boolean isQuit() {
    return quitAt != null;
  }

  @JsonProperty("players")
  public List<UserEntity> getPlayers() {
    return users;
  }

  public void setPlayers(final List<UserEntity> players) {
    this.users = players;
  }

  @JsonIgnore
  public boolean isComplete() {
    return isOver() || isQuit();
  }
}
