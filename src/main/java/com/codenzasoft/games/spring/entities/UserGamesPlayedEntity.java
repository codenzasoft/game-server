package com.codenzasoft.games.spring.entities;

import com.codenzasoft.games.spring.entities.ids.UserGamesPlayedId;
import com.codenzasoft.games.spring.services.GameType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.Date;
import java.util.UUID;

/** Persists a summary of games played by a user for a specific game type. */
@Entity(name = "USER_GAMES_PLAYED")
@IdClass(UserGamesPlayedId.class)
public class UserGamesPlayedEntity {

  @Id
  @Column(nullable = false, name = "user_id")
  UUID userId;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, name = "game_type")
  GameType gameType;

  @Column(nullable = false, name = "wins")
  int wins;

  @Column(nullable = false, name = "losses")
  int losses;

  @Column(nullable = false, name = "draws")
  int draws;

  @Column(nullable = false, name = "last_played_at")
  Date lastPlayedAt;

  public UserGamesPlayedEntity() {}

  public UserGamesPlayedEntity(
      final UUID userId,
      final GameType gameType,
      final int wins,
      final int losses,
      final int draws,
      final Date lastPlayedAt) {
    this.userId = userId;
    this.gameType = gameType;
    this.wins = wins;
    this.losses = losses;
    this.draws = draws;
    this.lastPlayedAt = lastPlayedAt;
  }

  @JsonProperty("userId")
  public UUID getUserId() {
    return userId;
  }

  @JsonProperty("gameType")
  public GameType getGameType() {
    return gameType;
  }

  @JsonProperty("wins")
  public int getWins() {
    return wins;
  }

  public void setWins(final int wins) {
    this.wins = wins;
  }

  @JsonProperty("losses")
  public int getLosses() {
    return losses;
  }

  public void setLosses(final int losses) {
    this.losses = losses;
  }

  @JsonProperty("draws")
  public int getDraws() {
    return draws;
  }

  public void setDraws(final int draws) {
    this.draws = draws;
  }

  @JsonProperty("lastPlayedAt")
  public Date getLastPlayedAt() {
    return lastPlayedAt;
  }

  public void setLastPlayedAt(final Date lastPlayedAt) {
    this.lastPlayedAt = lastPlayedAt;
  }
}
