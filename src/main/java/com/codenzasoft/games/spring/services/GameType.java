package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.xo.XoGame;

/** The types of {@link Game}s on the game server. */
public enum GameType {
  /** X's and O's type. */
  XO,
  /** KingsHand type. */
  KH,
  /** Hearts type. */
  HEARTS;

  public static <T> GameType fromGame(final Game<T> game) {
    if (game instanceof HeartsGame) {
      return HEARTS;
    }
    if (game instanceof XoGame) {
      return XO;
    }
    return KH;
  }
}
