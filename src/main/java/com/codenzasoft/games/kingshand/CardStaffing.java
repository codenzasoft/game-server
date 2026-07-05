package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of how workers have been allocated to a single table card during the {@link Phase#PLAY}
 * phase.
 *
 * <p>Instances are computed by {@link GameEngine#computeTableStaffing()} and stored on {@link
 * GameState} so the client can render the staffing indicator without recalculating.
 *
 * @param workersAllocated the number of workers assigned to this card from the available pool
 * @param workerCost the card's full {@link Card#staffRequirement()}
 * @param tier the resulting {@link StaffingTier} based on the ratio of allocated to required
 *     workers
 */
public record CardStaffing(int workersAllocated, int workerCost, StaffingTier tier) {

  /**
   * Determines the distribution of {@code floor(population)} workers left-to-right across the
   * provided cards, filling each card's {@link Card#staffRequirement()} greedily in slot order, and
   * returns the resulting {@link CardStaffing} list (an entry per card).
   *
   * <p>The half threshold for {@link StaffingTier#HALF} is {@code ceil(staffRequirement / 2)}.
   *
   * @param cards The cards to staff.
   * @param resources Available resources.
   */
  public static List<CardStaffing> computeTableStaffing(
      final List<Card> cards, final ResourceState resources) {
    int remaining = (int) Math.floor(resources.population());
    List<CardStaffing> result = new ArrayList<>(cards.size());

    for (Card card : cards) {
      int cost = (int) card.staffRequirement();
      int allocated = Math.min(remaining, cost);
      remaining -= allocated;

      int halfThreshold = (int) Math.ceil(cost / 2.0);
      StaffingTier tier;
      if (allocated >= cost) {
        tier = StaffingTier.FULL;
      } else if (allocated >= halfThreshold) {
        tier = StaffingTier.HALF;
      } else {
        tier = StaffingTier.NONE;
      }

      result.add(new CardStaffing(allocated, cost, tier));
    }

    return result;
  }
}
