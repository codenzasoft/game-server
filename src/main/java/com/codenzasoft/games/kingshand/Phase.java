package com.codenzasoft.games.kingshand;

public enum Phase {
  ADD,
  RANDOM_EVENT,
  PLAY;

  /** Returns the next phase in the normal turn cycle. */
  public Phase next() {
    return switch (this) {
      case ADD -> RANDOM_EVENT;
      case RANDOM_EVENT -> PLAY;
      case PLAY -> ADD;
    };
  }
}
