package com.codenzasoft.games.kingshand;

/**
 * A single effect attached to a {@link Card}. Each effect is parameterised over a {@link
 * EffectTarget} (population/production/food/another card), a numeric magnitude, a {@link ValueType}
 * (additive vs percentage), and a {@link FrequencyType} (one-shot vs every season).
 *
 * <p>The arithmetic of "given a current value, what's the new value after this effect fires" is
 * captured in {@link #nextValue(double)}; how and when it gets called is the resolver's
 * responsibility, not the effect's.
 */
public record Effect(
    EffectTarget target, double modifier, ValueType valueType, FrequencyType frequencyType) {

  /** Apply this effect to a current numeric value and return the new value. */
  public double nextValue(double value) {
    return switch (valueType) {
      case INTEGER -> value + modifier;
      case PERCENTAGE -> value + (value * (modifier * 0.01));
    };
  }
}
