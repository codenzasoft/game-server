package com.codenzasoft.games.kingshand;

/**
 * The three staffing tiers that determine how a card resolves during {@link Phase#PLAY}.
 *
 * <p>Workers ({@code floor(population)}) are distributed left-to-right across the current season's
 * table. Each card's {@link Card#staffRequirement()} is filled greedily in slot order:
 *
 * <ul>
 *   <li>{@link #FULL} — all required workers are available. The card fires at full value.
 *   <li>{@link #HALF} — at least {@code ceil(staffRequirement / 2)} workers are available. The card
 *       fires with every effect modifier floored to half.
 *   <li>{@link #NONE} — fewer than half the required workers are available. The card does not fire.
 * </ul>
 */
public enum StaffingTier {
  NONE,
  HALF,
  FULL
}
