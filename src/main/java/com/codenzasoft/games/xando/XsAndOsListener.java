package com.codenzasoft.games.xando;

public interface XsAndOsListener {

  void gameStarted(final XsAndOs game, final State state);

  void gameChanged(final XsAndOs game, final State state);

  void gameOver(final XsAndOs game, final State state);

  void gameQuit(final XsAndOs game, final State state);
}
