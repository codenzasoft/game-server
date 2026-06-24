package com.codenzasoft.games.kingshand;

/** What kind of one-time effect a {@link Decree} applies when accepted. */
public enum DecreeType {
  /** Permanently boosts (or adds) a {@link ResourceTarget} effect on the advisor's own card. */
  PASSIVE_BOOST,
  /**
   * Permanently boosts a completed Great Work's own output. Requires {@link
   * Decree#targetGreatWorkName()} to already be completed somewhere on the board.
   */
  GREAT_WORK_BOOST,
  /** Permanently raises the population cap by {@link Decree#effectAmount()}. */
  POPULATION_CAP_BOOST,
  /**
   * Immediately advances a Great Work currently under construction by one season (skipping its
   * per-season cost). Only available while some Great Work is mid-construction and has not already
   * received this acceleration once before.
   */
  GREAT_WORK_ACCELERATE,
  /**
   * Permanently grants additional free ADD-phase rerolls each season (unconditional, not tied to
   * this advisor remaining seated).
   */
  FREE_REROLL
}
