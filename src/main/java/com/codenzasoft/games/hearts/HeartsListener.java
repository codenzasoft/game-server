package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import java.util.List;

/** A {@link HeartsListener} is notified of all changes in {@link GameState}. */
public interface HeartsListener {
  void gameStarted(final GameState game);

  void gameOver(final GameState game);

  void gameQuit(final GameState game);

  void roundStarted(final GameState game, final RoundState round);

  void roundEnded(final GameState game, final RoundState round);

  void trickStarted(final GameState game, final RoundState round, final TrickState trick);

  void trickEnded(final GameState game, final RoundState round, final TrickState trick);

  void cardPlayed(
      final GameState game,
      final RoundState round,
      final TrickState trick,
      Player player,
      final Card card,
      int cardNumber);

  void passSelected(
      final GameState game, final RoundState round, final Player player, final List<Card> cards);

  void passComplete(final GameState game, final RoundState round);
}
