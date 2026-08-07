package com.codenzasoft.games.kingshand;

import java.util.*;
import java.util.stream.Stream;

/**
 * Immutable ordered collection of {@link Card}s backing a King's Hand pile (board / draw / discard)
 * or a hand. All structural operations return a new {@code Deck}; the backing list is an
 * unmodifiable defensive copy, so instances are safe to share and snapshot.
 *
 * <p><b>Matching modes.</b> Some operations match by record equality ({@link #contains}, {@link
 * #indexOf}, {@link #replace}, {@link #subListAfter}) and some by card name ({@link #containsName},
 * {@link #findByName}). These are deliberately distinct: an engine-boosted table card shares its
 * {@link Card#name()} with the hand original but is not {@code equals} to it. Preserve each
 * caller's original mode when wiring this into {@code PlayerState}.
 */
public record Deck(List<Card> cards) {

  public Deck {
    cards = List.copyOf(cards);
  }

  // ----- Factories -----

  /** An empty deck. */
  public static Deck empty() {
    return new Deck(List.of());
  }

  /** A deck over {@code cards} (the compact constructor copies, so no copy needed here). */
  public static Deck of(List<Card> cards) {
    return new Deck(cards);
  }

  /** A deck over the given cards in order. */
  public static Deck of(Card... cards) {
    return new Deck(List.of(cards));
  }

  // ----- Structural (return a new Deck; never mutate) -----

  /** This deck with {@code card} appended. */
  public Deck add(Card card) {
    List<Card> next = new ArrayList<>(cards);
    next.add(card);
    return new Deck(next);
  }

  /** This deck with every card in {@code toAdd} appended, in order. */
  public Deck addAll(List<Card> toAdd) {
    List<Card> next = new ArrayList<>(cards);
    next.addAll(toAdd);
    return new Deck(next);
  }

  /** This deck with the first occurrence of {@code card} (by equality) removed; no-op if absent. */
  public Deck remove(Card card) {
    List<Card> next = new ArrayList<>(cards);
    next.remove(card);
    return new Deck(next);
  }

  /**
   * This deck with the card at {@code index} removed.
   *
   * @throws IndexOutOfBoundsException if {@code index} is out of range
   */
  public Deck removeAt(int index) {
    List<Card> next = new ArrayList<>(cards);
    next.remove(index);
    return new Deck(next);
  }

  /**
   * This deck with the first occurrence of {@code from} (by equality) replaced by {@code to}.
   *
   * @throws IllegalArgumentException if {@code from} is not present
   */
  public Deck replace(Card from, Card to) {
    int i = this.indexOf(from);
    if (i < 0) {
      throw new IllegalArgumentException("card not found in deck: " + from.name());
    }
    List<Card> next = new ArrayList<>(cards);
    next.set(i, to);
    return new Deck(next);
  }

  // ----- Query -----

  /** {@code true} if {@code card} is present by record equality. */
  public boolean contains(Card card) {
    return cards.contains(card);
  }

  /** Index of the first occurrence of {@code card} by record equality, or {@code -1}. */
  public int indexOf(Card card) {
    return cards.indexOf(card);
  }

  /** {@code true} if any card's {@link Card#name()} equals {@code name}. */
  public boolean containsName(String name) {
    return cards.stream().anyMatch(c -> c.name().equals(name));
  }

  /** First card whose {@link Card#name()} equals {@code name}, or empty. */
  public Optional<Card> findByName(String name) {
    return cards.stream().filter(c -> c.name().equals(name)).findFirst();
  }

  /**
   * The cards positioned after the first occurrence of {@code card} (by equality). Empty if {@code
   * card} is absent or is the last card. The returned list is an independent copy.
   */
  public List<Card> subListAfter(Card card) {
    int i = cards.indexOf(card);
    if (i < 0 || i == cards.size() - 1) {
      return List.of();
    }
    return List.copyOf(cards.subList(i + 1, cards.size()));
  }

  /** Number of cards. */
  public int size() {
    return cards.size();
  }

  /** {@code true} if the deck holds no cards. */
  public boolean isEmpty() {
    return cards.isEmpty();
  }

  /** A stream over the cards, in order. */
  public Stream<Card> stream() {
    return cards.stream();
  }

  /** The backing cards as an unmodifiable list (already a defensive copy). */
  public List<Card> asList() {
    return cards;
  }

  // ----- Domain API -----

  /** This deck shuffled with {@code rng} (deterministic for a given seed). */
  public Deck shuffle(Random rng) {
    List<Card> next = new ArrayList<>(cards);
    Collections.shuffle(next, rng);
    return new Deck(next);
  }

  /** The cards drawn off the top of a deck, plus the deck that remains. */
  public record DrawResult(List<Card> drawn, Deck remaining) {}

  /**
   * Draws the top {@code count} cards. If {@code count} exceeds {@link #size()} it is clamped, so
   * the whole deck is drawn and {@code remaining} is empty. {@code count == 0} draws nothing.
   *
   * @throws IllegalArgumentException if {@code count} is negative
   */
  public DrawResult drawTop(int count) {
    if (count < 0) {
      throw new IllegalArgumentException("cannot draw a negative count: " + count);
    }
    int n = Math.min(count, cards.size());
    List<Card> drawn = List.copyOf(cards.subList(0, n));
    Deck remaining = new Deck(cards.subList(n, cards.size()));
    return new DrawResult(drawn, remaining);
  }
}
