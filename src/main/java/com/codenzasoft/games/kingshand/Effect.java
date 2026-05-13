package com.codenzasoft.games.kingshand;

/**
 * A single effect attached to a {@link Card}. Each effect is associated with a {@link EffectTarget}
 * (population/production/food/another card), a numeric magnitude, and a {@link ValueType} (additive
 * vs percentage).
 *
 * <p>Frequency (one-shot vs every season) is a property of the owning {@link Card}, not of
 * individual effects — all effects on a card share the same {@link FrequencyType}.
 *
 * <p>The arithmetic of "given a current value, what's the new value after this effect fires" is
 * captured in {@link #apply(double)}; how and when it gets called is the resolver's responsibility,
 * not the effect's.
 */
public record Effect(EffectTarget target, double modifier, ValueType valueType) {

  /** Apply this effect to a current numeric value and return the new value. */
  public double apply(double value) {
    return switch (valueType) {
      case INTEGER -> value + modifier;
      case PERCENTAGE -> value + (value * (modifier * 0.01));
    };
  }

  /** Returns a new {@link Effect} with the modifier scaled by {@code factor}. */
  public Effect upgrade(double factor) {
    return new Effect(target, modifier * factor, valueType);
  }
}
