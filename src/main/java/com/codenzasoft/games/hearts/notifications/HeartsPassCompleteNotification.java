package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;

public class HeartsPassCompleteNotification extends HeartsNotification {

  private final RoundState round;

  public HeartsPassCompleteNotification(final RoundState round) {
    this.round = round;
  }

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.passComplete(game, state, round);
  }
}
