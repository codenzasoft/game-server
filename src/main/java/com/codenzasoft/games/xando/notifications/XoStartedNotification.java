package com.codenzasoft.games.xando.notifications;

import com.codenzasoft.games.xando.XoState;
import com.codenzasoft.games.xando.XoGame;
import com.codenzasoft.games.xando.XoGameListener;

public class GameStartedNotification extends GameNotification {

  @Override
  void notifyListener(final XoGame game, final XoState state, final XoGameListener listener) {
    listener.gameStarted(game, state);
  }
}
