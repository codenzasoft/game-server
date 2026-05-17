package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import java.util.List;

/** A {@link HeartsListener} is notified of all changes in {@link HeartsState}. */
public interface HeartsListener {
  void gameStarted(final HeartsGame game, final HeartsState state);

  void gameOver(final HeartsGame game, final HeartsState state);

  void gameQuit(final HeartsGame game, final HeartsState state);

  void roundStarted(final HeartsGame game, final HeartsState state, final RoundState round);

  void roundEnded(final HeartsGame game, final HeartsState state, final RoundState round);

  void trickStarted(
      final HeartsGame game,
      final HeartsState state,
      final RoundState round,
      final TrickState trick);

  void trickEnded(
      final HeartsGame game,
      final HeartsState state,
      final RoundState round,
      final TrickState trick);

  void cardPlayed(
      final HeartsGame game,
      final HeartsState state,
      final RoundState round,
      final TrickState trick,
      Player player,
      final Card card,
      int cardNumber);

  void passSelected(
      final HeartsGame game,
      final HeartsState state,
      final RoundState round,
      final Player player,
      final List<Card> cards);

  void passComplete(final HeartsGame game, final HeartsState state, final RoundState round);
}
