package com.codenzasoft.games.xando;

/** The pieces for a game of X's and O's. */
public enum Piece {
  X,
  O;

  public Piece opposite() {
    return switch (this) {
      case X -> O;
      case O -> X;
    };
  }
}
