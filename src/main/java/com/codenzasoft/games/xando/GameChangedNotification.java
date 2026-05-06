package com.codenzasoft.games.xando;

public class GameChangedNotification extends GameNotification {

  @Override
  void notifyListener(final XsAndOs game, final State state, final XsAndOsListener listener) {
    listener.gameChanged(game, state);
  }
}
