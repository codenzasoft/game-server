package com.codenzasoft.games.kingshand;

import java.util.List;

/**
 * The result of playing a {@link Card}.
 *
 * @param card The {@link Card} that was played.
 * @param tier The {@link StaffingTier} applied at the time the card was played.
 * @param fired Whether the card fired.
 * @param delta The {@link ResourceState} delta the play resulted in.
 * @param boosted A list of any cards that were boosted.
 */
public record PlayedCard(
    Card card, StaffingTier tier, boolean fired, ResourceState delta, List<Card> boosted) {

  public ResolutionStep getResolutionStep() {
    return new ResolutionStep(card.name(), tier(), card.isEngineBoost(), delta());
  }
}
