package com.codenzasoft.games.kingshand;

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
public record CardStaffing(int workersAllocated, int workerCost, StaffingTier tier) {}
