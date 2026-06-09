package com.codenzasoft.games.kingshand;

public interface KhObserver {
  void gameStarted(final GameEngine game, final GameState state);

  void gameOver(final GameEngine game, final GameState state);
}
