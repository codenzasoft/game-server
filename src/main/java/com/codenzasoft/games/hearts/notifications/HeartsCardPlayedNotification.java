package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;

public class HeartsCardPlayedNotification extends HeartsNotification {

  private final RoundState round;
  private final TrickState trick;
  private final Player player;
  private final Card card;
  private final int cardNumber;

  public HeartsCardPlayedNotification(
      final RoundState round,
      final TrickState trick,
      final Player player,
      final Card card,
      final int cardNumber) {
    this.round = round;
    this.trick = trick;
    this.player = player;
    this.card = card;
    this.cardNumber = cardNumber;
  }

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.cardPlayed(game, state, player, card, cardNumber);
  }
}
