package com.codenzasoft.games.kingshand.notifications;

import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.KhObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KhNotification {

    private static final Logger log = LoggerFactory.getLogger(com.codenzasoft.games.kingshand.notifications.KhNotification.class);

    public void execute(
            final GameEngine game, final GameState state, final KhObserver listener) {
        try {
            notifyListener(game, state, listener);
        } catch (Exception e) {
            log.error("GameState notification failed", e);
        }
    }

    abstract void notifyListener(
            final GameEngine game, final GameState state, final KhObserver listener);
}
