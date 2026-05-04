package com.codenzasoft.games.kingshand;

public record Effect(double modifier, ValueType valueType, FrequencyType frequencyType) {

  public double nextValue(double value) {
    return switch (valueType) {
      case INTEGER -> value + modifier;
      case PERCENTAGE -> value + (value * (modifier * 0.01));
    };
  }
}
