package com.codenzasoft.games.kingshand;

import java.util.List;

/**
 * A playable card. Carries its play requirements ({@code staffRequirement}, {@code
 * productionRequirement}), a {@link FrequencyType} that applies to all of its effects (one-shot vs
 * every season), and an arbitrary list of {@link Effect}s.
 *
 * <p>All effects on a card share the same {@link FrequencyType}; the engine reads frequency once
 * per card during resolution rather than per-effect.
 *
 * <p>The effects list is defensively copied at construction so callers can't mutate it after the
 * fact.
 */
public record Card(
    String name,
    double staffRequirement,
    double productionRequirement,
    FrequencyType frequencyType,
    List<Effect> effects) {

  public Card {
    effects = effects == null ? List.of() : List.copyOf(effects);
  }

  /**
   * Returns a new {@link Card} with the modifier of every {@link ValueType#INTEGER} effect whose
   * target equals {@code resource} increased by {@code delta}. All other effects are preserved
   * unchanged. Used by the engine-builder pass to permanently boost cards to the right of an
   * engine-builder card during {@link Phase#PLAY} resolution.
   *
   * @param resource the resource target to boost
   * @param delta the flat amount to add to each matching effect's modifier
   */
  public Card boostEffect(ResourceTarget resource, double delta) {
    List<Effect> boosted =
        effects.stream()
            .map(
                e ->
                    (e.target() == resource && e.valueType() == ValueType.INTEGER)
                        ? new Effect(e.target(), e.modifier() + delta, e.valueType())
                        : e)
            .toList();
    return new Card(name, staffRequirement, productionRequirement, frequencyType, boosted);
  }

  /**
   * Returns a new {@link Card} with identical requirements but every effect's modifier multiplied
   * by {@code factor}. Used by the upgrade path when a duplicate is placed on the table.
   *
   * @param factor the multiplier to apply to every effect modifier (e.g. {@code 2} to double)
   */
  public Card amplify(double factor) {
    List<Effect> amplified = effects.stream().map(e -> e.upgrade(factor)).toList();
    // Append a roman numeral suffix so upgraded cards are visually distinct and
    // distinguishable from their base version by name alone.
    String upgradedName = name.endsWith(" II") ? name.replace(" II", " III") : name + " II";
    return new Card(
        upgradedName, staffRequirement, productionRequirement, frequencyType, amplified);
  }
}
