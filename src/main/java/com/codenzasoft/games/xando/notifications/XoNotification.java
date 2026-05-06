package com.codenzasoft.games.xando.notifications;

import com.codenzasoft.games.xando.XoState;
import com.codenzasoft.games.xando.XoGame;
import com.codenzasoft.games.xando.XoGameListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GameNotification {

  private static final Logger log = LoggerFactory.getLogger(GameNotification.class);

  public void execute(final XoGame game, final XoState state, final XoGameListener listener) {
    try {
      notifyListener(game, state, listener);
    } catch (Exception e) {
      log.error("XoState notification failed", e);
    }
  }

  abstract void notifyListener(
      final XoGame game, final XoState state, final XoGameListener listener);
}
