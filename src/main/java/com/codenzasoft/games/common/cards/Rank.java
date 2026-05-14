package com.codenzasoft.games.common.cards;

import java.util.Optional;

public enum Rank {
  TWO,
  THREE,
  FOUR,
  FIVE,
  SIX,
  SEVEN,
  EIGHT,
  NINE,
  TEN,
  JACK,
  QUEEN,
  KING,
  ACE;

  public Optional<Rank> getPrevious() {
    if (this.ordinal() == 0) {
      return Optional.empty();
    }
    return Optional.of(values()[this.ordinal() - 1]);
  }
}
