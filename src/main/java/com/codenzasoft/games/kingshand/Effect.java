package com.codenzasoft.games.kingshand;

/**
 * A single effect attached to a {@link Card}. Each effect is associated with a {@link EffectTarget}
 * (population/production/food/another card/the opponent) and a flat additive numeric magnitude.
 *
 * <p>Frequency (one-shot vs every season) is a property of the owning {@link Card}, not of
 * individual effects — all effects on a card share the same {@link FrequencyType}.
 *
 * <p>The arithmetic of "given a current value, what's the new value after this effect fires" is
 * captured in {@link #apply(double)}; how and when it gets called is the resolver's responsibility,
 * not the effect's.
 */
public record Effect(EffectTarget target, double modifier) {

  /** Apply this effect to a current numeric value and return the new value. */
  public double apply(double value) {
    return value + modifier;
  }

  /**
   * {@code true} when this effect is an <em>attack</em>: a negative change to a kingdom's
   * POPULATION. Used during cross-seat resolution to route the hit through the victim's defensive
   * STRENGTH before it reduces population.
   */
  public boolean isAttack() {
    return target() == ResourceTarget.POPULATION && modifier() < 0.0;
  }

  /** Returns a new {@link Effect} with the modifier scaled by {@code factor}. */
  public Effect upgrade(double factor) {
    return new Effect(target, modifier * factor);
  }
}
