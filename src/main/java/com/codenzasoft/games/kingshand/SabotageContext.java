package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.List;

/**
 * Carries the frozen opponent snapshot into {@link Card#play} during PLAY resolution and collects
 * the cross-seat (sabotage) deltas produced by {@link OpponentResourceTarget} effects.
 *
 * <p>Deltas are collected during each table's resolution and applied to the live opponent kingdom
 * only after every table has finished its own resolution — the two-phase apply that keeps
 * simultaneous resolution order-independent.
 *
 * @param opponentSnapshot the opponent's resources frozen before any table resolved; {@code null}
 *     when the match has no live opponent (single-player), in which case sabotage effects no-op
 * @param opponentDeltas flat additive effects to apply to the live opponent after all
 *     self-resolution completes
 */
public record SabotageContext(ResourceState opponentSnapshot, List<Effect> opponentDeltas) {

  /** A context with no opponent: sabotage effects are silently skipped. */
  public static SabotageContext none() {
    return new SabotageContext(null, new ArrayList<>());
  }

  /** A collecting context for an opponent frozen at {@code snapshot}. */
  public static SabotageContext against(ResourceState snapshot) {
    return new SabotageContext(snapshot, new ArrayList<>());
  }

  /**
   * Records the flat delta of {@code effect} (whose target is an {@link OpponentResourceTarget})
   * for the post-resolution apply. No-ops when there is no opponent.
   */
  public void collect(Effect effect) {
    if (opponentSnapshot == null) {
      return;
    }
    if (!(effect.target() instanceof OpponentResourceTarget target)) {
      return;
    }
    opponentDeltas.add(new Effect(target.targetResource(), effect.modifier()));
  }
}
