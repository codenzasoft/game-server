package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Derives the current strategic context from live game state.
 *
 * <p>Moon-shot detection uses two thresholds:
 *
 * <ul>
 *   <li>While Q♠ is still unplayed, all round points are pure hearts. {@link
 *       #HEARTS_ONLY_THRESHOLD} fires after a player collects that many hearts, catching a
 *       hearts-first shooter well before they acquire Q♠.
 *   <li>Once Q♠ has been played, {@link #SHOOT_THREAT_THRESHOLD} fires on Q♠ + at least one heart
 *       (14 pts). This intentionally excludes Q♠ alone (13 pts) to avoid false positives.
 * </ul>
 */
public final class RoundContext {

  /** Points threshold used after Q♠ has been played: Q♠ + at least one heart. */
  public static final int SHOOT_THREAT_THRESHOLD = 14;

  /**
   * Hearts-only threshold (Q♠ still in play): flag after this many hearts. Deliberately lower so
   * that a hearts-first shooter is detected early enough to interfere.
   */
  public static final int HEARTS_ONLY_THRESHOLD = 4;

  private RoundContext() {}

  /**
   * Returns the player who appears to be shooting the moon, based on accumulated round points.
   * Returns empty if no player meets the applicable threshold or the round is already complete.
   */
  public static Optional<String> shootingPlayer(final HeartsState state) {
    if (state.getCurrentRound().isEmpty()) return Optional.empty();
    final RoundState round = state.getRound();
    if (round.isComplete()) return Optional.empty();

    final Map<String, Integer> scores = round.getScore();

    if (StrategyHelper.hasQueenBeenPlayed(round)) {
      // Q♠ is already in someone's score; only a Q♠ + heart combination is a real threat
      return scores.entrySet().stream()
          .filter(e -> e.getValue() >= SHOOT_THREAT_THRESHOLD)
          .max(Comparator.comparing(Map.Entry::getValue))
          .map(Map.Entry::getKey);
    }

    // Q♠ still in play — all points so far are hearts. Catch hearts-first accumulation early.
    return scores.entrySet().stream()
        .filter(e -> e.getValue() >= HEARTS_ONLY_THRESHOLD)
        .max(Comparator.comparing(Map.Entry::getValue))
        .map(Map.Entry::getKey);
  }

  /**
   * Returns whether the given player's moon-shot attempt has failed — i.e., any completed trick
   * this round with point cards was won by someone else.
   */
  public static boolean hasMoonShotFailed(final HeartsState state, final String playerName) {
    if (state.getCurrentRound().isEmpty()) return false;
    return state.getRound().tricks().stream()
        .filter(TrickState::isComplete)
        .filter(t -> StrategyHelper.getPoints(t) > 0)
        .anyMatch(t -> t.winner() != null && !t.winner().player().equals(playerName));
  }

  /**
   * Resolves the current strategic mode for the given player.
   *
   * <ul>
   *   <li>{@link Mode#OFFENSIVE} — this player is the detected shooter
   *   <li>{@link Mode#SABOTEUR} — another player is the detected shooter
   *   <li>{@link Mode#DEFENSIVE} — no moon-shot threat detected
   * </ul>
   */
  public static Mode resolveMode(final HeartsState state, final String playerName) {
    final Optional<String> shooter = shootingPlayer(state);
    if (shooter.filter(p -> p.equals(playerName)).isPresent()) {
      return Mode.OFFENSIVE;
    }
    if (shooter.isPresent()) {
      return Mode.SABOTEUR;
    }
    return Mode.DEFENSIVE;
  }
}
