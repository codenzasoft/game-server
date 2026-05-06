package com.codenzasoft.games.xando;

public class GameStartedNotification extends GameNotification {

  @Override
  void notifyListener(final XsAndOs game, final State state, final XsAndOsListener listener) {
    listener.gameStarted(game, state);
  }
}
