package com.codenzasoft.games.kingshand;

public enum Phase {
  ADD,
  RANDOM_EVENT,
  PLAY,
  /** Terminal phase: the player's population has reached zero. No further actions are possible. */
  GAME_OVER;

  /** Returns the next phase in the normal turn cycle. {@link #GAME_OVER} is terminal. */
  public Phase next() {
    return switch (this) {
      case ADD -> RANDOM_EVENT;
      case RANDOM_EVENT -> PLAY;
      case PLAY -> ADD;
      case GAME_OVER -> GAME_OVER;
    };
  }
}
