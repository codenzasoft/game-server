package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HeartsNotification {

  private static final Logger log = LoggerFactory.getLogger(HeartsNotification.class);

  public void execute(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    try {
      notifyListener(game, state, listener);
    } catch (Exception e) {
      log.error("HeartsState notification failed", e);
    }
  }

  abstract void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener);
}
