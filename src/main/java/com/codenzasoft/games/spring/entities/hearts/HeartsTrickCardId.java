package com.codenzasoft.games.spring.entities.hearts;

import jakarta.persistence.Column;
import java.util.Objects;
import java.util.UUID;

public class HeartsTrickCardId {
  @Column(name = "game_id")
  UUID gameId;

  @Column(name = "round_number")
  int roundNumber;

  @Column(name = "trick_number")
  int trickNumber;

  @Column(name = "play_order")
  int playOrder;

  public HeartsTrickCardId() {}

  public HeartsTrickCardId(UUID gameId, int roundNumber, int trickNumber, int playOrder) {
    this.gameId = gameId;
    this.roundNumber = roundNumber;
    this.trickNumber = trickNumber;
    this.playOrder = playOrder;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HeartsTrickCardId that = (HeartsTrickCardId) o;
    return roundNumber == that.roundNumber
        && trickNumber == that.trickNumber
        && playOrder == that.playOrder
        && Objects.equals(gameId, that.gameId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gameId, roundNumber, trickNumber, playOrder);
  }
}
