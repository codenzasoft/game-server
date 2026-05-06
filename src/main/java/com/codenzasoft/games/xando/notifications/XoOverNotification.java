package com.codenzasoft.games.xando.notifications;

import com.codenzasoft.games.xando.XoGame;
import com.codenzasoft.games.xando.XoGameListener;
import com.codenzasoft.games.xando.XoState;

public class XoOverNotification extends XoNotification {

  @Override
  void notifyListener(final XoGame game, final XoState state, final XoGameListener listener) {
    listener.gameOver(game, state);
  }
}
