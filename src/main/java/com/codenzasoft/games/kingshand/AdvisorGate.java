package com.codenzasoft.games.kingshand;

/**
 * Gates when an {@link Advisor} becomes eligible to appear in the COUNCIL_ADD offer pool. Most
 * advisors are always eligible ({@link #NONE}); a few are deliberately withheld until the run has
 * reached a certain milestone, giving the offer pool a sense of progression.
 *
 * <p>Evaluated by {@code GameEngine.isAdvisorGateOpen(Advisor)}.
 */
public enum AdvisorGate {
  /** Always eligible, from the very first COUNCIL_ADD offer. */
  NONE,
  /** Eligible only once BARRACKS has completed (i.e. {@code STRENGTH} is unlocked). */
  BARRACKS_COMPLETED,
  /** Eligible only once at least one Great Work has been commissioned (construction started). */
  ANY_GREAT_WORK_COMMISSIONED,
  /** Eligible only once THE_GREAT_ROAD has been commissioned (construction started). */
  GREAT_ROAD_COMMISSIONED
}
