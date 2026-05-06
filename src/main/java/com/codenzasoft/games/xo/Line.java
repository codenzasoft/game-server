package com.codenzasoft.games.xo;

import java.util.List;

/**
 * A line in a game of X's and O's. A line is made up of three cells forming a horizontal, vertical,
 * or diagonal line.
 *
 * @param positions
 * @param piece
 */
public record Line(List<Position> positions, Piece piece) {}
