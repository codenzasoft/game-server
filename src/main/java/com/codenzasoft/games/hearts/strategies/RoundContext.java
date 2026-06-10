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
 * <p>A player is considered a moon-shot threat once they have accumulated at least {@link
 * #SHOOT_THREAT_THRESHOLD} points in the current round. This requires Q♠ plus at least one heart —
 * accidentally picking up Q♠ alone (13 pts) does not cross the threshold, reducing false positives.
 */
public final class RoundContext {

  /** Points in the current round at which a player is treated as attempting to shoot. Tunable. */
  public static final int SHOOT_THREAT_THRESHOLD = 14;

  private RoundContext() {}

  /**
   * Returns the player who appears to be shooting the moon, based on accumulated round points.
   * Returns empty if no player meets the threshold or the round is already complete.
   */
  public static Optional<String> shootingPlayer(final HeartsState state) {
    if (state.getCurrentRound().isEmpty()) return Optional.empty();
    final RoundState round = state.getRound();
    if (round.isComplete()) return Optional.empty();

    return round.getScore().entrySet().stream()
        .filter(e -> e.getValue() >= SHOOT_THREAT_THRESHOLD)
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
