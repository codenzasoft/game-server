package com.codenzasoft.games.kingshand;

import java.util.List;

public record PlayedCard(
    Card card, StaffingTier tier, boolean fired, ResourceState delta, List<Card> boosted) {

  public ResolutionStep getResolutionStep() {
    return new ResolutionStep(card.name(), tier(), card.isEngineBoost(), delta());
  }
}
