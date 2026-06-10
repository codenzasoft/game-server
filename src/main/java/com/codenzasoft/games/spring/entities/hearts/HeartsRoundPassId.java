package com.codenzasoft.games.spring.entities.hearts;

import jakarta.persistence.Column;
import java.util.Objects;
import java.util.UUID;

public class HeartsRoundPassId {
  @Column(name = "game_id")
  UUID gameId;

  @Column(name = "round_number")
  int roundNumber;

  @Column(name = "user_id")
  UUID userId;

  @Column(name = "card_rank")
  String cardRank;

  @Column(name = "card_suit")
  String cardSuit;

  public HeartsRoundPassId() {}

  public HeartsRoundPassId(
      UUID gameId, int roundNumber, UUID userId, String cardRank, String cardSuit) {
    this.gameId = gameId;
    this.roundNumber = roundNumber;
    this.userId = userId;
    this.cardRank = cardRank;
    this.cardSuit = cardSuit;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HeartsRoundPassId that = (HeartsRoundPassId) o;
    return roundNumber == that.roundNumber
        && Objects.equals(gameId, that.gameId)
        && Objects.equals(userId, that.userId)
        && Objects.equals(cardRank, that.cardRank)
        && Objects.equals(cardSuit, that.cardSuit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gameId, roundNumber, userId, cardRank, cardSuit);
  }
}
