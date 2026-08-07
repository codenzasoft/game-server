package com.codenzasoft.games.kingshand.strategies;

import com.codenzasoft.games.kingshand.Card;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.Season;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Runs candidate actions the live match using the real {@link GameEngine}, so every simulated
 * consequence — production costs, staffing, card effects, recurring effects, food consumption,
 * growth, Great Work construction, cross-seat combat, death/victory checks — is computed by the
 * exact same code that runs a real game. The simulator never mutates the live state: {@link
 * GameState}/{@link com.codenzasoft.games.kingshand.PlayerState} are immutable, so a throwaway
 * {@link GameEngine} run against {@code real} leaves {@code real} untouched and produces the
 * simulated outcome as a new state read back via {@link GameEngine#getState()}.
 */
public final class KhSimulator {

  private KhSimulator() {}

  /**
   * Clones {@code real}, applies {@code seat}'s PLAY plan (place the given hand cards, optionally
   * commission a Great Work, then reorder the table via {@code orderChooser}), resolves the season
   * for every live seat, and returns the resolved copy for scoring.
   *
   * <p>Opponent seats are driven to a plain end-and-run with no new placements — the bot cannot see
   * their hands, so it assumes they add nothing this turn. This is only used to compute the bot's
   * own projected resolution; nothing here touches the live game.
   *
   * @param orderChooser given the post-placement table, returns the desired card-name order; may
   *     return {@code null} to leave the order untouched
   */
  public static GameState simulatePlay(
      final GameState state,
      final int seat,
      final List<Card> placements,
      final String commission,
      final Function<List<Card>, List<String>> orderChooser,
      final Random rng) {
    // real is immutable — the engine wraps it and produces new states internally, never touching
    // it.
    final GameEngine engine = new GameEngine(state, rng);
    final Season season = state.getTime().season();

    engine.setActiveSeat(seat);
    for (final Card card : placements) {
      try {
        engine.placeCard(card);
      } catch (final RuntimeException ignored) {
        // A pre-check disagreed with validation (e.g. production ran out mid-plan); skip this card.
      }
    }
    if (commission != null) {
      try {
        engine.commissionGreatWork(commission);
      } catch (final RuntimeException ignored) {
        // Not commissionable in this state; the plan proceeds without it.
      }
    }
    if (orderChooser != null) {
      final List<Card> table = engine.getState().getPlayer(seat).getTable(season);
      final List<String> order = orderChooser.apply(table);
      if (order != null && order.size() == table.size()) {
        try {
          engine.reorderTable(order);
        } catch (final RuntimeException ignored) {
          // Fall back to the natural order.
        }
      }
    }

    resolveAllSeats(engine, seat);
    return engine.getState();
  }

  /**
   * Clones {@code real} and applies an ADD-phase choice ({@code pickCard} when {@code card !=
   * null}, otherwise {@code skipAdd}), returning the copy. Used to evaluate a draft against a real
   * cloned deck without disturbing the live game.
   */
  public static GameState simulateAdd(
      final GameState state, final int seat, final Card card, final Random rng) {
    final GameEngine engine = new GameEngine(state, rng);
    engine.setActiveSeat(seat);
    try {
      if (card != null) {
        engine.pickCard(card);
      } else {
        engine.skipAdd();
      }
    } catch (final RuntimeException ignored) {
      // Leave the state unchanged if the pick was rejected.
    }
    return engine.getState();
  }

  /** Ends the season for every live seat so the barrier releases and resolution runs. */
  private static void resolveAllSeats(final GameEngine engine, final int seat) {
    final int count = engine.getState().playerCount();
    for (int other = 0; other < count; other++) {
      if (other == seat || engine.getState().getPlayer(other).isGameOver()) {
        continue;
      }
      engine.setActiveSeat(other);
      try {
        engine.endAndRun();
      } catch (final RuntimeException ignored) {
        // already submitted / out — ignore
      }
    }
    engine.setActiveSeat(seat);
    try {
      engine.endAndRun();
    } catch (final RuntimeException ignored) {
      // already resolved
    }
  }
}
