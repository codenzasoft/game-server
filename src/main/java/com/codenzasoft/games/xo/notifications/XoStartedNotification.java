package com.codenzasoft.games.xo.notifications;

import com.codenzasoft.games.xo.XoGame;
import com.codenzasoft.games.xo.XoGameListener;
import com.codenzasoft.games.xo.XoState;

public class XoStartedNotification extends XoNotification {

  @Override
  void notifyListener(final XoGame game, final XoState state, final XoGameListener listener) {
    listener.gameStarted(game, state);
  }
}
