package com.codenzasoft.games.kingshand;

import java.util.List;

/**
 * A playable card. Carries its play requirements ({@code populationRequirement}, {@code
 * productionRequirement}) and an arbitrary list of {@link Effect}s, each with its own target /
 * magnitude / frequency / condition.
 *
 * <p>The effects list is defensively copied at construction so callers can't mutate it after the
 * fact.
 */
public record Card(
    double populationRequirement, double productionRequirement, List<Effect> effects) {

  public Card {
    effects = effects == null ? List.of() : List.copyOf(effects);
  }
}
