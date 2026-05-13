package com.codenzasoft.games.kingshand;

/**
 * A global resource an {@link Effect} can read or write.
 *
 * <p>Factory helpers create {@link Effect} instances by {@link ValueType}. Frequency is no longer
 * embedded in the effect — it is a property of the owning {@link Card}.
 */
public enum ResourceTarget implements EffectTarget {
  POPULATION,
  PRODUCTION,
  FOOD;

  /** Creates an additive (flat integer) effect targeting this resource. */
  public Effect integer(int value) {
    return new Effect(this, (double) value, ValueType.INTEGER);
  }

  /** Creates a percentage-based effect targeting this resource. */
  public Effect percentage(double value) {
    return new Effect(this, value, ValueType.PERCENTAGE);
  }
}
