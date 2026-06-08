package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

public class HeartsCardPlayedNotification extends HeartsNotification {

  private final Player player;
  private final Card card;
  private final int cardNumber;

  public HeartsCardPlayedNotification(final Player player, final Card card, final int cardNumber) {
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
