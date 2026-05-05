package com.codenzasoft.games.kingshand;

public enum Phase {
  ADD,
  RANDOM_EVENT,
  PLAY;

  public Phase next() {
    return values()[(this.ordinal() + 1) % values().length];
  }
}
