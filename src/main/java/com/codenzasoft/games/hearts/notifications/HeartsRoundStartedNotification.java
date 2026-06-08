package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

public class HeartsRoundStartedNotification extends HeartsNotification {

  public HeartsRoundStartedNotification() {}

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.roundStarted(game, state);
  }
}
