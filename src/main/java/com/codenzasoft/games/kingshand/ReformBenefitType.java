package com.codenzasoft.games.kingshand;

/**
 * What kind of benefit a {@link Reform} stack grants. Every Reform also carries a card-effect cost
 * (see {@link Reform#costResource()}/{@link Reform#costPerStack()}) regardless of benefit type —
 * the trade-off is always paid as a permanent drain on the advisor's own seated card.
 */
public enum ReformBenefitType {
  /** Boosts (or adds) a {@link ResourceTarget} effect on the advisor's own seated card. */
  CARD_EFFECT,
  /** Grants additional free ADD-phase rerolls each season while this advisor is seated. */
  FREE_REROLL,
  /** Reveals additional candidate cards in every ADD-phase offer while this advisor is seated. */
  EXTRA_ADD_CANDIDATE,
  /** Increases the cross-table production boost amount (Brynn Ostlar's unique mechanic). */
  CROSS_TABLE_BOOST
}
