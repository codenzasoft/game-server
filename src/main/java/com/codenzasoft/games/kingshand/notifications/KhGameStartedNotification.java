package com.codenzasoft.games.kingshand.notifications;

import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.KhObserver;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.hearts.notifications.KhNotification;

public class KhGameStartedNotification extends KhNotification {

    @Override
    void notifyListener(
            final GameEngine game, final GameState state, final KhObserver listener) {
        listener.gameStarted(game, state);
    }
}
