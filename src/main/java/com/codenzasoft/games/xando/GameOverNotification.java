package com.codenzasoft.games.xando;

public class GameOverNotification extends GameNotification {

  @Override
  void notifyListener(final XsAndOs game, final State state, final XsAndOsListener listener) {
    listener.gameOver(game, state);
  }
}
