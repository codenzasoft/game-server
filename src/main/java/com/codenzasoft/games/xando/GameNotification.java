package com.codenzasoft.games.xando;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GameNotification {

  private static final Logger log = LoggerFactory.getLogger(GameNotification.class);

  public void execute(final XsAndOs game, final State state, final XsAndOsListener listener) {
    try {
      notifyListener(game, state, listener);
    } catch (Exception e) {
      log.error("State notification failed", e);
    }
  }

  abstract void notifyListener(
      final XsAndOs game, final State state, final XsAndOsListener listener);
}
