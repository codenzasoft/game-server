package com.codenzasoft.games.kingshand;

import java.util.List;

/**
 * Central catalog of all {@link Card} definitions for King's Hand.
 *
 * <p>Each card is declared as a {@code public static final Card} constant so it can be referenced
 * from tests, deck-builders, and the engine without instantiating cards inline. Add new cards by
 * appending another constant below — keep them grouped by season / theme as the catalog grows.
 *
 * <p>This class is non-instantiable.
 */
public final class Catalog {

  private Catalog() {}

  public static final Card FARMSTEAD =
      new Card(
          0.0,
          50.0,
          List.of(
              new Effect(
                  ResourceTarget.POPULATION, 2.0, ValueType.INTEGER, FrequencyType.RECURRING),
              new Effect(
                  ResourceTarget.POPULATION, 2.0, ValueType.INTEGER, FrequencyType.RECURRING)));

  public static final Card HUNTING =
      new Card(
          15.0,
          20.0,
          List.of(new Effect(ResourceTarget.FOOD, 30.0, ValueType.INTEGER, FrequencyType.SINGLE)));

  public static final Card WORKSHOP =
      new Card(
          5.0,
          40.0,
          List.of(
              new Effect(
                  ResourceTarget.PRODUCTION, 10.0, ValueType.PERCENTAGE, FrequencyType.RECURRING)));

  public static final Card SETTLEMENT =
      new Card(
          20.0,
          100.0,
          List.of(
              new Effect(
                  ResourceTarget.POPULATION, 3.0, ValueType.INTEGER, FrequencyType.RECURRING)));

  public static final Card FARM =
      new Card(
          2.0,
          30.0,
          List.of(
              new Effect(ResourceTarget.FOOD, 10.0, ValueType.INTEGER, FrequencyType.RECURRING)));

  public static final Card SEEDS =
      new Card(
          1.0,
          10.0,
          List.of(new Effect(ResourceTarget.FOOD, 8.0, ValueType.INTEGER, FrequencyType.SINGLE)));

  public static final Card FERTILIZER =
      new Card(
          1.0,
          15.0,
          List.of(
              new Effect(
                  ResourceTarget.FOOD, 10.0, ValueType.PERCENTAGE, FrequencyType.RECURRING)));

  public static final Card MINE =
      new Card(
          5.0,
          60.0,
          List.of(
              new Effect(
                  ResourceTarget.PRODUCTION, 15.0, ValueType.INTEGER, FrequencyType.RECURRING)));

  public static final Card LOGGING =
      new Card(
          3.0,
          25.0,
          List.of(
              new Effect(
                  ResourceTarget.PRODUCTION, 10.0, ValueType.INTEGER, FrequencyType.RECURRING)));

  public static final Card TRADE_ROUTES =
      new Card(
          4.0,
          80.0,
          List.of(
              new Effect(
                  ResourceTarget.PRODUCTION, 5.0, ValueType.PERCENTAGE, FrequencyType.RECURRING),
              new Effect(ResourceTarget.FOOD, 5.0, ValueType.PERCENTAGE, FrequencyType.RECURRING)));

  public static final Card FISHING_VILLAGE =
      new Card(
          3.0,
          40.0,
          List.of(
              new Effect(ResourceTarget.FOOD, 15.0, ValueType.INTEGER, FrequencyType.RECURRING)));

  public static final Card SCHOOL_HOUSE =
      new Card(
          2.0,
          50.0,
          List.of(
              new Effect(
                  ResourceTarget.POPULATION, 1.0, ValueType.INTEGER, FrequencyType.RECURRING)));

  public static final Card MANOR =
      new Card(
          8.0,
          120.0,
          List.of(
              new Effect(
                  ResourceTarget.POPULATION, 2.0, ValueType.INTEGER, FrequencyType.RECURRING),
              new Effect(
                  ResourceTarget.PRODUCTION, 20.0, ValueType.INTEGER, FrequencyType.RECURRING)));

  public static final Card HARVEST =
      new Card(
          2.0,
          10.0,
          List.of(new Effect(ResourceTarget.FOOD, 40.0, ValueType.INTEGER, FrequencyType.SINGLE)));

  public static final Card TEXTILES =
      new Card(
          4.0,
          60.0,
          List.of(
              new Effect(
                  ResourceTarget.PRODUCTION, 20.0, ValueType.PERCENTAGE, FrequencyType.RECURRING)));

  public static final Card FORGE =
      new Card(
          3.0,
          50.0,
          List.of(
              new Effect(
                  ResourceTarget.PRODUCTION, 15.0, ValueType.INTEGER, FrequencyType.RECURRING)));

  public static final Card WOODPILES =
      new Card(
          1.0,
          10.0,
          List.of(
              new Effect(
                  ResourceTarget.PRODUCTION, 25.0, ValueType.INTEGER, FrequencyType.SINGLE)));

  public static final Card GRANARY =
      new Card(
          2.0,
          40.0,
          List.of(
              new Effect(ResourceTarget.FOOD, 10.0, ValueType.PERCENTAGE, FrequencyType.RECURRING),
              new Effect(ResourceTarget.FOOD, 20.0, ValueType.INTEGER, FrequencyType.SINGLE)));

  // ----- Convenience accessor -----

  /** All cards declared in this catalog, in declaration order. */
  public static List<Card> all() {
    return List.of(
        FARMSTEAD,
        HUNTING,
        WORKSHOP,
        SETTLEMENT,
        FARM,
        SEEDS,
        FERTILIZER,
        MINE,
        LOGGING,
        TRADE_ROUTES,
        FISHING_VILLAGE,
        SCHOOL_HOUSE,
        MANOR,
        HARVEST,
        TEXTILES,
        FORGE,
        WOODPILES,
        GRANARY);
  }
}
