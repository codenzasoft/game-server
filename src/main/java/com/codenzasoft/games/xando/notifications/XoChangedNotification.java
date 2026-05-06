package com.codenzasoft.games.xando.notifications;

import com.codenzasoft.games.xando.XoGame;
import com.codenzasoft.games.xando.XoGameListener;
import com.codenzasoft.games.xando.XoState;

public class XoChangedNotification extends XoNotification {

  @Override
  void notifyListener(final XoGame game, final XoState state, final XoGameListener listener) {
    listener.gameChanged(game, state);
  }
}
