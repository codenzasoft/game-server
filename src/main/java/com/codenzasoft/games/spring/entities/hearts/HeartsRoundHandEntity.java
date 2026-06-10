package com.codenzasoft.games.spring.entities.hearts;

import jakarta.persistence.*;
import java.util.UUID;

@Entity(name = "HEARTS_ROUND_HANDS")
@IdClass(HeartsRoundHandId.class)
public class HeartsRoundHandEntity {

  @Id
  @Column(nullable = false, name = "game_id")
  UUID gameId;

  @Id
  @Column(nullable = false, name = "round_number")
  int roundNumber;

  @Id
  @Column(nullable = false, name = "user_id")
  UUID userId;

  @Id
  @Column(nullable = false, name = "card_rank")
  String cardRank;

  @Id
  @Column(nullable = false, name = "card_suit")
  String cardSuit;

  public HeartsRoundHandEntity() {}

  public HeartsRoundHandEntity(
      UUID gameId, int roundNumber, UUID userId, String cardRank, String cardSuit) {
    this.gameId = gameId;
    this.roundNumber = roundNumber;
    this.userId = userId;
    this.cardRank = cardRank;
    this.cardSuit = cardSuit;
  }

  public UUID getGameId() {
    return gameId;
  }

  public int getRoundNumber() {
    return roundNumber;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getCardRank() {
    return cardRank;
  }

  public String getCardSuit() {
    return cardSuit;
  }
}
