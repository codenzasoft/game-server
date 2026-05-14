package com.codenzasoft.games.hearts;

public enum Pass {
  LEFT(1),
  RIGHT(-1),
  ACROSS(2),
  KEEPER(0);

  private final int offset;

  Pass(int offset) {
    this.offset = offset;
  }

  public int getOffset() {
    return offset;
  }

  public Pass nextPass() {
    return values()[(this.ordinal() + 1) % values().length];
  }
}
