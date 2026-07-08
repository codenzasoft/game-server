package com.codenzasoft.games.kingshand;

/**
 * Listener for King's Hand match lifecycle and state-change events. Implemented by the broadcast
 * layer so client pushes are driven by the engine rather than the controller.
 */
public interface KhObserver {
  /** Fired once when a game is first initialized. */
  void gameStarted(final GameEngine game, final GameState state);

  /**
   * Fired after an action mutates the match state, so listeners can push the fresh state to clients.
   * Triggered by {@link GameEngine#notifyUpdated()} at the request boundary.
   */
  void gameUpdated(final GameEngine game, final GameState state);

  /** Fired when the match ends (a kingdom is knocked out or completes its Great Works). */
  void gameOver(final GameEngine game, final GameState state);
}
