package com.codenzasoft.games.kingshand.notifications;

import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.KhObserver;

public class KhGameUpdatedNotification extends KhNotification {

  @Override
  void notifyListener(final GameEngine game, final GameState state, final KhObserver listener) {
    listener.gameUpdated(game, state);
  }
}
