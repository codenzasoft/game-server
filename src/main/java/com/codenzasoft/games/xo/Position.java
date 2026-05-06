package com.codenzasoft.games.xo;

/**
 * A position of a cell in a game of X's and O's. A {@link Position} is defined as the row-column
 * coordinates of a cell, where the top left corner is {1,1} and the bottom right corner is {3,3}.
 *
 * @param row The row coordinate (1-3)
 * @param column The column coordinate (1-3)
 */
public record Position(int row, int column) {
  static final int MIN_COUNT = 1;
  static final int ROW_COUNT = 3;
  static final int COLUMN_COUNT = 3;

  public static Position of(int row, int column) {
    if (row < MIN_COUNT || row > ROW_COUNT) {
      throw new IllegalArgumentException(String.format("Row %d is out of bounds", row));
    }
    if (column < MIN_COUNT || column > COLUMN_COUNT) {
      throw new IllegalArgumentException(String.format("Column %d is out of bounds", column));
    }
    return new Position(row, column);
  }
}
