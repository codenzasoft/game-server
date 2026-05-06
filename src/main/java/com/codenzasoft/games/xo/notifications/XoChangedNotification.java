package com.codenzasoft.games.xo.notifications;

import com.codenzasoft.games.xo.XoGame;
import com.codenzasoft.games.xo.XoGameListener;
import com.codenzasoft.games.xo.XoState;

public class XoChangedNotification extends XoNotification {

  @Override
  void notifyListener(final XoGame game, final XoState state, final XoGameListener listener) {
    listener.gameChanged(game, state);
  }
}
