package com.codenzasoft.games.common;

/**
 * Common lifecycle notifications that any {@link Game} can deliver, regardless of game type. This
 * lets shared services such as the {@link com.codenzasoft.games.spring.services.GameCache} and
 * {@code GameService} react to a game's lifecycle (caching it while active, updating
 * games-played statistics when it ends, etc.) without depending on per-game-type listener
 * interfaces.
 *
 * @param <T> The underlying game implementation type, e.g. {@code GameEngine} or {@code
 *     HeartsGame}.
 */
public interface GameLifecycleObserver<T> {

  /**
   * Called when a game is started for the first time.
   *
   * @param game The game that started.
   */
  void gameStarted(Game<T> game);

  /**
   * Called when a previously started game is resumed, e.g. rebuilt from persisted state after a
   * cache miss.
   *
   * @param game The game that resumed.
   */
  void gameResumed(Game<T> game);

  /**
   * Called when a game ends naturally, e.g. a win/loss condition is reached.
   *
   * @param game The game that is over.
   */
  void gameOver(Game<T> game);

  /**
   * Called when a game ends because a player quit before it was otherwise over.
   *
   * @param game The game that was quit.
   */
  void gameQuit(Game<T> game);
}
