package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

public class HeartsGameOverNotification extends HeartsNotification {

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.gameOver(game, state);
  }
}
