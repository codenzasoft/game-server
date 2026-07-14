package com.codenzasoft.games.kingshand;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A sabotage effect target: acts on the <em>opponent's</em> kingdom rather than the card owner's.
 *
 * <p>Resolution rule (locked, see KH-MULTIPLAYER-PLAN.md Slice 3): during PLAY resolution every
 * kingdom is snapshotted <em>before</em> any table resolves; sabotage effects are evaluated against
 * the frozen opponent snapshot, collected as flat deltas, and applied only after all self-resolution
 * completes. This keeps simultaneous resolution deterministic and order-independent — a sabotage
 * effect never reads a mid-resolution value.
 *
 * @param targetResource the opponent resource this effect drains or boosts
 */
public record OpponentResourceTarget(ResourceTarget targetResource) implements EffectTarget {

  /**
   * Wire discriminator so the client can tell this target apart from a {@link CardTarget} (both
   * serialize a {@code targetResource} field).
   */
  @JsonProperty("opponent")
  public boolean opponent() {
    return true;
  }
}
