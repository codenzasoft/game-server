package com.codenzasoft.games.xando;

public class GameQuitNotification extends GameNotification {

  @Override
  void notifyListener(final XsAndOs game, final State state, final XsAndOsListener listener) {
    listener.gameQuit(game, state);
  }
}
