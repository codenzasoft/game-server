package com.codenzasoft.games.kingshand;

/** A global resource an {@link Effect} can read or write. */
public enum ResourceTarget implements EffectTarget {
  POPULATION,
  PRODUCTION,
  FOOD;

  public Effect once(int value) {
    return new Effect(this, (double) value, ValueType.INTEGER, FrequencyType.SINGLE);
  }

  public Effect recurring(int value) {
    return new Effect(this, (double) value, ValueType.INTEGER, FrequencyType.RECURRING);
  }

  public Effect once(double value) {
    return new Effect(this, value, ValueType.PERCENTAGE, FrequencyType.SINGLE);
  }

  public Effect recurring(double value) {
    return new Effect(this, value, ValueType.PERCENTAGE, FrequencyType.RECURRING);
  }
}
