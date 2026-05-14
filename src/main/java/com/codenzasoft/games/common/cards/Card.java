package com.codenzasoft.games.common.cards;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Card(Suit suit, Rank rank) {

  @JsonProperty("suit")
  public Suit getSuit() {
    return suit;
  }

  @JsonProperty("rank")
  public Rank getRank() {
    return rank;
  }

  @JsonIgnore
  public boolean outRanks(final Card card) {
    return this.outRanks(card.getRank());
  }

  @JsonIgnore
  public boolean outRanks(final Rank rank) {
    return this.getRank().ordinal() > rank.ordinal();
  }

  @JsonIgnore
  @Override
  public String toString() {
    return "Card{" + "suit=" + suit + ", rank=" + rank + '}';
  }
}
