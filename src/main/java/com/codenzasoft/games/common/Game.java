package com.codenzasoft.games.common;

import java.util.List;
import java.util.UUID;

/**
 * Games of different types implement this interface to support common features.
 *
 * <ul>
 *   <li>Caching in the {@link com.codenzasoft.games.spring.services.GameCache}.
 *   <li>Displaying players associated with a game.
 * </ul>
 *
 * @param <T>
 */
public interface Game<T> {

  /**
   * Returns the type of this game.
   *
   * @return A {@link GameType}.
   */
  GameType getGameType();

  /**
   * Returns the specific underlying implementation of the game. For example a {@link
   * com.codenzasoft.games.xo.XoGame}.
   *
   * @return The underlying game.
   */
  T getGame();

  /**
   * Returns the game id.
   *
   * @return a {@link UUID}
   */
  UUID getId();

  /**
   * Returns a list of players playing the game.
   *
   * @return A list of {@link Player}s.
   */
  List<Player> getPlayers();
}
