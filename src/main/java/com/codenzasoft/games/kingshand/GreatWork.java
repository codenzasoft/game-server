package com.codenzasoft.games.kingshand;

import java.util.Set;

/**
 * Immutable definition of a Great Work: a multi-season construction project that, once complete,
 * permanently replaces itself with a powerful card on the table.
 *
 * <p>The life cycle of a Great Work during a game:
 *
 * <ol>
 *   <li>The player commissions it during a {@link Phase#PLAY} phase (paying a one-time production
 *       cost). The {@link #underConstructionCard()} is placed on the table occupying one slot.
 *   <li>Each season that the card is fully staffed and the player can afford its negative effects,
 *       the construction period decrements. If unaffordable, neither costs nor the period change.
 *   <li>When the period reaches zero, the under-construction card is swapped out for {@link
 *       #completedCard()}, any {@link #unlocksResources()} are activated, and the win condition is
 *       re-evaluated if {@link #isWinCondition()} is {@code true}.
 * </ol>
 *
 * <p>Two Great Works are resource unlockers:
 *
 * <ul>
 *   <li><b>TREASURY</b> (Summer table) — unlocks {@link ResourceTarget#GOLD}.
 *   <li><b>BARRACKS</b> (Fall table) — unlocks {@link ResourceTarget#STRENGTH}.
 * </ul>
 *
 * <p>Four Great Works are win conditions (one per season). Completing all four ends the game in
 * victory. Win-condition Great Works require both GOLD and STRENGTH to be unlocked before they can
 * be commissioned.
 *
 * @param name                  unique stable identifier (e.g. {@code "TREASURY"})
 * @param underConstructionCard card placed on the table while construction is in progress; its
 *                              effects are the per-season construction costs (negative modifiers)
 * @param completedCard         card that replaces the under-construction card once the period
 *                              reaches zero; carries powerful recurring effects
 * @param initialPeriod         number of successful construction seasons required to complete
 * @param tableSlot             which season's table this Great Work is built on; can only be
 *                              commissioned when the current season matches
 * @param isWinCondition        {@code true} for the four seasonal victory Great Works
 * @param unlocksResources      resource types activated upon completion (empty for most)
 */
public record GreatWork(
    String name,
    Card underConstructionCard,
    Card completedCard,
    int initialPeriod,
    Season tableSlot,
    boolean isWinCondition,
    Set<ResourceTarget> unlocksResources) {

  public GreatWork {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("GreatWork name cannot be null or blank");
    }
    if (underConstructionCard == null) {
      throw new IllegalArgumentException("underConstructionCard cannot be null");
    }
    if (completedCard == null) {
      throw new IllegalArgumentException("completedCard cannot be null");
    }
    if (initialPeriod < 1) {
      throw new IllegalArgumentException("initialPeriod must be >= 1");
    }
    if (tableSlot == null) {
      throw new IllegalArgumentException("tableSlot cannot be null");
    }
    unlocksResources = unlocksResources == null ? Set.of() : Set.copyOf(unlocksResources);
  }
}
