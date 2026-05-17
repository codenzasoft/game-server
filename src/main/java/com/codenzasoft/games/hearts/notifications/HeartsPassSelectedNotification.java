package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;
import java.util.List;

public class HeartsPassSelectedNotification extends HeartsNotification {

  private final RoundState round;
  private final Player player;
  private final List<Card> cards;

  public HeartsPassSelectedNotification(
      final RoundState round, final Player player, final List<Card> cards) {
    this.round = round;
    this.player = player;
    this.cards = cards;
  }

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.passSelected(game, state, round, player, cards);
  }
}
