package com.codenzasoft.games.xando.notifications;

import com.codenzasoft.games.xando.State;
import com.codenzasoft.games.xando.XsAndOs;
import com.codenzasoft.games.xando.XsAndOsListener;

public class GameOverNotification extends GameNotification {

  @Override
  void notifyListener(final XsAndOs game, final State state, final XsAndOsListener listener) {
    listener.gameOver(game, state);
  }
}
