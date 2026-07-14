package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.List;

/**
 * Carries the frozen opponent snapshot into {@link Card#play} during PLAY resolution and collects
 * the cross-seat (sabotage) deltas produced by {@link OpponentResourceTarget} effects.
 *
 * <p>Deltas are resolved against {@link #opponentSnapshot()} at collection time (percentages are
 * flattened to integer deltas against the snapshot value) and applied to the live opponent kingdom
 * only after every table has finished its own resolution — the two-phase apply that keeps
 * simultaneous resolution order-independent.
 *
 * @param opponentSnapshot the opponent's resources frozen before any table resolved; {@code null}
 *     when the match has no live opponent (single-player), in which case sabotage effects no-op
 * @param opponentDeltas flat {@link ValueType#INTEGER} effects to apply to the live opponent after
 *     all self-resolution completes
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
   * Resolves {@code effect} (whose target is an {@link OpponentResourceTarget}) against the frozen
   * snapshot and records the resulting flat delta. No-ops when there is no opponent.
   */
  public void collect(Effect effect) {
    if (opponentSnapshot == null) {
      return;
    }
    if (!(effect.target() instanceof OpponentResourceTarget target)) {
      return;
    }
    final ResourceTarget resource = target.targetResource();
    final double delta;
    if (effect.valueType() == ValueType.PERCENTAGE) {
      // Flatten the percentage against the frozen snapshot value, never a live mid-resolution one.
      delta = snapshotValue(resource) * (effect.modifier() * 0.01);
    } else {
      delta = effect.modifier();
    }
    opponentDeltas.add(new Effect(resource, delta, ValueType.INTEGER));
  }

  private double snapshotValue(ResourceTarget resource) {
    return switch (resource) {
      case POPULATION -> opponentSnapshot.population();
      case PRODUCTION -> opponentSnapshot.production();
      case FOOD -> opponentSnapshot.food();
      case POPULATION_CAP -> opponentSnapshot.populationCap();
      case GOLD -> opponentSnapshot.gold();
      case STRENGTH -> opponentSnapshot.strength();
    };
  }
}
