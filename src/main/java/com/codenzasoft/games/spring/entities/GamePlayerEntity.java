package com.codenzasoft.games.spring.entities;

import com.codenzasoft.games.spring.entities.ids.GamePlayerId;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.UUID;

@Entity(name = "GAME_PLAYERS")
@IdClass(GamePlayerId.class)
public class GamePlayerEntity {
  @Id
  @Column(nullable = false, name = "game_id")
  UUID gameId;

  @Id
  @Column(nullable = false, name = "user_id")
  UUID userId;

  @Column(nullable = false, name = "player_order")
  int order;

  @OneToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "game_id", insertable = false, updatable = false)
  GameEntity game;

  public GamePlayerEntity() {}

  public GamePlayerEntity(final UUID gameId, final UUID userId, final int order) {
    this.gameId = gameId;
    this.userId = userId;
    this.order = order;
  }

  @JsonProperty("gameId")
  public UUID getGameId() {
    return gameId;
  }

  public void setGameId(final UUID gameId) {
    this.gameId = gameId;
  }

  @JsonProperty("userId")
  public UUID getUserId() {
    return userId;
  }

  public void setUserId(final UUID userId) {
    this.userId = userId;
  }

  @JsonProperty("order")
  public int getOrder() {
    return order;
  }

  public void setOrder(final int order) {
    this.order = order;
  }

  public GameEntity getGame() {
    return game;
  }
}
