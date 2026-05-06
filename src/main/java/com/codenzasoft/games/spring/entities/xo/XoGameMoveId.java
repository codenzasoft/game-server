package com.codenzasoft.games.spring.entities.xo;

import jakarta.persistence.Column;
import java.util.Objects;
import java.util.UUID;

public class XoGameMoveId {
  @Column(nullable = false, name = "game_id")
  UUID gameId;

  @Column(nullable = false, name = "user_id")
  UUID userId;

  @Column(nullable = false, name = "move_order")
  int moveOrder;

  public XoGameMoveId() {}

  public XoGameMoveId(UUID gameId, UUID userId, int moveOrder) {
    this.gameId = gameId;
    this.userId = userId;
    this.moveOrder = moveOrder;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    XoGameMoveId that = (XoGameMoveId) o;
    return moveOrder == that.moveOrder
        && Objects.equals(gameId, that.gameId)
        && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gameId, userId, moveOrder);
  }
}
