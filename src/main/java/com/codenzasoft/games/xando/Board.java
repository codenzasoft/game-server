package com.codenzasoft.games.xando;

import java.util.*;

/**
 * Returns a new {@link Board}.
 *
 * @param leader The hearts leading {@link Piece}.
 * @param positions The {@link Position}s held by {@link Piece}, in the order they were played.
 */
public record Board(int sequence, Piece leader, Map<Piece, List<Position>> positions) {

  private static final List<List<Position>> LINES =
      List.of(
          // rows
          List.of(Position.of(1, 1), Position.of(1, 2), Position.of(1, 3)),
          List.of(Position.of(2, 1), Position.of(2, 2), Position.of(2, 3)),
          List.of(Position.of(3, 1), Position.of(3, 2), Position.of(3, 3)),

          // columns
          List.of(Position.of(1, 1), Position.of(2, 1), Position.of(3, 1)),
          List.of(Position.of(1, 2), Position.of(2, 2), Position.of(3, 2)),
          List.of(Position.of(1, 3), Position.of(2, 3), Position.of(3, 3)),

          // diagonals
          List.of(Position.of(1, 1), Position.of(2, 2), Position.of(3, 3)),
          List.of(Position.of(3, 1), Position.of(2, 2), Position.of(1, 3)));

  public static Board emptyBoard(final Piece leader) {
    return new Board(0, leader, Map.of());
  }

  /**
   * Returns the {@link Piece} at the provided position.
   *
   * @param position Board {@link Position}.
   * @return The {@link Piece} at the {@link Position} or empty, if none.
   */
  public Optional<Piece> getPiece(final Position position) {
    return positions().entrySet().stream()
        .filter(e -> e.getValue().contains(position))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  public List<Position> getPositions(final Piece piece) {
    return positions().getOrDefault(piece, List.of());
  }

  public List<Position> getEmptyPositions() {
    final List<Position> empty = new ArrayList<>();
    for (int row = 1; row <= Position.ROW_COUNT; row++) {
      for (int column = 1; column <= Position.COLUMN_COUNT; column++) {
        if (getPiece(new Position(row, column)).isEmpty()) {
          empty.add(new Position(row, column));
        }
      }
    }
    return empty;
  }

  public Optional<Line> getWinner() {
    return LINES.stream()
        .map(this::getLine)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  public List<Optional<Piece>> getPieces(final List<Position> positions) {
    return positions.stream().map(this::getPiece).toList();
  }

  Optional<Line> getLine(final List<Position> positions) {
    final List<Optional<Piece>> pieces = getPieces(positions);
    if (pieces.stream().allMatch(Optional::isPresent)) {
      if (pieces.stream().map(Optional::get).distinct().count() == 1) {
        return Optional.of(new Line(Collections.unmodifiableList(positions), pieces.get(0).get()));
      }
    }
    return Optional.empty();
  }

  Board set(final Position position, final Piece piece) {
    validate(position);
    if (getWinner().isPresent()) {
      throw new IllegalStateException("hearts is over");
    }
    final Optional<Piece> existing = getPiece(position);
    if (existing.isPresent()) {
      throw new IllegalArgumentException("Position already contains " + existing.get());
    }
    final Map<Piece, List<Position>> nextPositions = new HashMap<>();
    for (final Piece p : Piece.values()) {
      final List<Position> next = new ArrayList<>(getPositions(p));
      if (p.equals(piece)) {
        next.add(position);
      }
      nextPositions.put(p, next);
    }
    return new Board(sequence + 1, leader(), Collections.unmodifiableMap(nextPositions));
  }

  void validate(final Position position) {
    if (position.row() < Position.MIN_COUNT || position.row() > Position.ROW_COUNT) {
      throw new IllegalArgumentException("Invalid position: " + position);
    }
    if (position.column() < Position.MIN_COUNT || position.column() > Position.COLUMN_COUNT) {
      throw new IllegalArgumentException("Invalid position: " + position);
    }
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("\n");
    for (int row = Position.MIN_COUNT; row <= Position.ROW_COUNT; row++) {
      for (int column = Position.MIN_COUNT; column <= Position.COLUMN_COUNT; column++) {
        final Optional<Piece> piece = getPiece(new Position(row, column));
        if (piece.isPresent()) {
          builder.append(piece.get().toString());
        } else {
          builder.append("-");
        }
      }
      builder.append("\n");
    }
    return builder.toString();
  }
}
