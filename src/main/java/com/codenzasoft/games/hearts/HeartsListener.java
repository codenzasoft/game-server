package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import java.util.List;

/** A {@link HeartsListener} is notified of all changes in {@link HeartsState}. */
public interface HeartsListener {
  void gameStarted(final HeartsGame game, final HeartsState state);

  void gameOver(final HeartsGame game, final HeartsState state);

  void gameQuit(final HeartsGame game, final HeartsState state);

  void roundStarted(final HeartsGame game, final HeartsState state);

  void roundEnded(final HeartsGame game, final HeartsState state);

  void trickStarted(final HeartsGame game, final HeartsState state);

  void trickEnded(final HeartsGame game, final HeartsState state);

  void cardPlayed(
      final HeartsGame game,
      final HeartsState state,
      Player player,
      final Card card,
      int cardNumber);

  void passSelected(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> cards);

  void passComplete(final HeartsGame game, final HeartsState state);
}
