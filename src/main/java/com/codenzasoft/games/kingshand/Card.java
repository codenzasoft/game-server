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

  /**
   * Returns a new {@link Card} with identical requirements but every effect's modifier multiplied
   * by {@code factor}. Used by the upgrade path when a duplicate is placed on the table.
   *
   * @param factor the multiplier to apply to every effect modifier (e.g. {@code 2} to double)
   */
  public Card amplify(double factor) {
    List<Effect> amplified = effects.stream().map(e -> e.upgrade(factor)).toList();
    return new Card(populationRequirement, productionRequirement, amplified);
  }
}
