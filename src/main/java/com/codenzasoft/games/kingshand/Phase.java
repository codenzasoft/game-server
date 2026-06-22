package com.codenzasoft.games.kingshand;

/**
 * The current phase of a King's Hand turn.
 *
 * <p>There is no generic {@code next()} cycle: {@code COUNCIL_ADD}/{@code COUNCIL_MEETING} are
 * only inserted once per year (after Winter's {@code PLAY} ends), and only once GOLD has been
 * unlocked, so the transition out of {@code PLAY} is conditional rather than a fixed rotation.
 * Every transition is instead made explicit at its call site in {@code GameEngine}:
 *
 * <ul>
 *   <li>{@code ADD} → {@code PLAY} via {@code pickCard}/{@code skipAdd}.
 *   <li>{@code PLAY} → {@code COUNCIL_ADD} (if Winter just ended and GOLD is unlocked) or straight
 *       to {@code ADD} (otherwise), via {@code endAndRun}/{@code advancePhase}.
 *   <li>{@code COUNCIL_ADD} → {@code COUNCIL_MEETING} via {@code hireAdvisor}/{@code
 *       hireAdvisorById}/{@code skipCouncilAdd}.
 *   <li>{@code COUNCIL_MEETING} → {@code ADD} via {@code acceptCouncilProposal}/{@code
 *       passCouncilProposal}.
 * </ul>
 */
public enum Phase {
  /** Player is choosing a card to add to their hand for the season. */
  ADD,
  /** Player is playing cards from hand onto the table and running the season. */
  PLAY,
  /** Once per year (post-Winter, GOLD unlocked only): player may hire a new advisor. */
  COUNCIL_ADD,
  /** Once per year, immediately after COUNCIL_ADD: the seated council proposes one project. */
  COUNCIL_MEETING
}
