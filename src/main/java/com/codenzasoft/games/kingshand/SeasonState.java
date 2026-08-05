package com.codenzasoft.games.kingshand;

/**
 * Immutable per-season card state for one kingdom: the three {@link Deck}s that live under a single
 * {@link Season} — the {@code board} (cards in play), the {@code drawPile}, and the {@code
 * discardPile}. Groups what were previously three parallel {@code Map<Season, List<Card>>} fields
 * on {@code PlayerState} into one cohesive value.
 *
 * <p>Every operation returns a new {@code SeasonState}; the {@link Deck}s are themselves immutable.
 */
public record SeasonState(Deck board, Deck drawPile, Deck discardPile) {

  /** A season with three empty piles. */
  public static SeasonState empty() {
    return new SeasonState(Deck.empty(), Deck.empty(), Deck.empty());
  }

  // ----- with* accessors -----

  public SeasonState withBoard(Deck board) {
    return new SeasonState(board, drawPile, discardPile);
  }

  public SeasonState withDrawPile(Deck drawPile) {
    return new SeasonState(board, drawPile, discardPile);
  }

  public SeasonState withDiscardPile(Deck discardPile) {
    return new SeasonState(board, drawPile, discardPile);
  }

  // ----- Board convenience delegates (mirror PlayerState's old table ops) -----

  /** This season with {@code card} appended to the board. */
  public SeasonState addToBoard(Card card) {
    return withBoard(board.add(card));
  }

  /** This season with the first occurrence of {@code card} removed from the board. */
  public SeasonState removeFromBoard(Card card) {
    return withBoard(board.remove(card));
  }

  /**
   * This season with the board card at {@code slot} removed.
   *
   * @throws IndexOutOfBoundsException if {@code slot} is out of range
   */
  public SeasonState removeFromBoardAt(int slot) {
    return withBoard(board.removeAt(slot));
  }

  /**
   * This season with the first occurrence of {@code from} on the board replaced by {@code to}.
   *
   * @throws IllegalArgumentException if {@code from} is not on the board
   */
  public SeasonState replaceOnBoard(Card from, Card to) {
    return withBoard(board.replace(from, to));
  }

  /** This season with {@code card} removed from the board and added to the discard pile. */
  public SeasonState discardFromBoard(Card card) {
    return new SeasonState(board.remove(card), drawPile, discardPile.add(card));
  }

  /** This season with every card in {@code hand} appended to the discard pile. */
  public SeasonState discardHand(Deck hand) {
    return withDiscardPile(discardPile.addAll(hand.asList()));
  }
}
