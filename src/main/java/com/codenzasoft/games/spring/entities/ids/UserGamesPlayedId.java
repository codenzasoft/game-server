package com.codenzasoft.games.spring.entities.ids;

import com.codenzasoft.games.spring.services.GameType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import java.util.UUID;

public class UserGamesPlayedId {

  @Column(nullable = false, name = "user_id")
  UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, name = "game_type")
  GameType gameType;

  public UserGamesPlayedId() {}

  public UserGamesPlayedId(final UUID userId, final GameType gameType) {
    this.userId = userId;
    this.gameType = gameType;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UserGamesPlayedId that = (UserGamesPlayedId) o;
    return Objects.equals(userId, that.userId) && gameType == that.gameType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, gameType);
  }
}
