package com.codenzasoft.games.xando.notifications;

import com.codenzasoft.games.xando.XoState;
import com.codenzasoft.games.xando.XoGame;
import com.codenzasoft.games.xando.XoGameListener;

public class GameOverNotification extends GameNotification {

  @Override
  void notifyListener(final XoGame game, final XoState state, final XoGameListener listener) {
    listener.gameOver(game, state);
  }
}
