package com.codenzasoft.games.spring.entities.ids;

import jakarta.persistence.Column;
import java.util.Objects;
import java.util.UUID;

public class GamePlayerId {
  @Column(nullable = false, name = "game_id")
  UUID gameId;

  @Column(nullable = false, name = "user_id")
  UUID userId;

  public GamePlayerId() {}

  public GamePlayerId(UUID gameId, UUID userId) {
    this.gameId = gameId;
    this.userId = userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GamePlayerId that = (GamePlayerId) o;
    return Objects.equals(gameId, that.gameId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gameId, userId);
  }
}
