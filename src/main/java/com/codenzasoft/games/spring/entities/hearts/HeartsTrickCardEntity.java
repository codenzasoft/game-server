package com.codenzasoft.games.spring.entities.hearts;

import jakarta.persistence.*;
import java.util.UUID;

@Entity(name = "HEARTS_TRICKS")
@IdClass(HeartsTrickCardId.class)
public class HeartsTrickCardEntity {

  @Id
  @Column(nullable = false, name = "game_id")
  UUID gameId;

  @Id
  @Column(nullable = false, name = "round_number")
  int roundNumber;

  @Id
  @Column(nullable = false, name = "trick_number")
  int trickNumber;

  @Id
  @Column(nullable = false, name = "play_order")
  int playOrder;

  @Column(nullable = false, name = "user_id")
  UUID userId;

  @Column(nullable = false, name = "card_rank")
  String cardRank;

  @Column(nullable = false, name = "card_suit")
  String cardSuit;

  public HeartsTrickCardEntity() {}

  public HeartsTrickCardEntity(
      UUID gameId,
      int roundNumber,
      int trickNumber,
      int playOrder,
      UUID userId,
      String cardRank,
      String cardSuit) {
    this.gameId = gameId;
    this.roundNumber = roundNumber;
    this.trickNumber = trickNumber;
    this.playOrder = playOrder;
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

  public int getTrickNumber() {
    return trickNumber;
  }

  public int getPlayOrder() {
    return playOrder;
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
