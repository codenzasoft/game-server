package com.codenzasoft.games.kingshand;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Seasonal catalog of all {@link Card} definitions for King's Hand.
 *
 * <p>Cards are organised by the {@link Season} in which they can be offered to the player during
 * the {@link Phase#ADD} phase. {@code offerAddChoices} draws exclusively from the catalog entry for
 * the current season, so a card cannot appear outside its intended season.
 *
 * <p>Individual cards are still exposed as {@code public static final} constants so they can be
 * referenced cheaply from tests and other catalog definitions without constructing a full catalog
 * instance.
 *
 * <p>Use {@link #defaultCatalog()} to obtain the canonical game catalog, or pass a custom {@code
 * Map<Season, List<Card>>} to the constructor to build a test / variant catalog.
 */
public class CardCatalog {

  private final Map<Season, List<Card>> seasonalCards;

  public CardCatalog(Map<Season, List<Card>> seasonalCards) {
    Map<Season, List<Card>> copy = new EnumMap<>(Season.class);
    for (Season s : Season.values()) {
      List<Card> cards = seasonalCards == null ? null : seasonalCards.get(s);
      copy.put(s, cards == null ? List.of() : List.copyOf(cards));
    }
    this.seasonalCards = Collections.unmodifiableMap(copy);
  }

  /** All cards available for offer during {@code season}. */
  public List<Card> forSeason(Season season) {
    if (season == null) {
      throw new IllegalArgumentException("season cannot be null");
    }
    return seasonalCards.get(season);
  }

  /** Flat list of every card across all seasons, in season order (SPRING → WINTER). */
  public List<Card> all() {
    return seasonalCards.values().stream().flatMap(List::stream).toList();
  }

  // ----- Card constants -----

  // Spring
  public static final Card FARM = new Card(2.0, 30.0, List.of(ResourceTarget.FOOD.recurring(10)));
  public static final Card SEEDS = new Card(1.0, 10.0, List.of(ResourceTarget.FOOD.once(8)));
  public static final Card FERTILIZER =
      new Card(1.0, 15.0, List.of(ResourceTarget.FOOD.recurring(10.0)));
  public static final Card MINE =
      new Card(5.0, 60.0, List.of(ResourceTarget.PRODUCTION.recurring(15)));

  // Summer
  public static final Card FARMSTEAD =
      new Card(
          0.0,
          50.0,
          List.of(ResourceTarget.POPULATION.recurring(2), ResourceTarget.POPULATION.recurring(2)));
  public static final Card HUNTING = new Card(15.0, 20.0, List.of(ResourceTarget.FOOD.once(30)));
  public static final Card SETTLEMENT =
      new Card(20.0, 100.0, List.of(ResourceTarget.POPULATION.recurring(3)));
  public static final Card LOGGING =
      new Card(3.0, 25.0, List.of(ResourceTarget.PRODUCTION.recurring(10)));
  public static final Card TRADE_ROUTES =
      new Card(
          4.0,
          80.0,
          List.of(ResourceTarget.PRODUCTION.recurring(5.0), ResourceTarget.FOOD.recurring(5.0)));
  public static final Card FISHING_VILLAGE =
      new Card(3.0, 40.0, List.of(ResourceTarget.FOOD.recurring(15)));
  public static final Card SCHOOL_HOUSE =
      new Card(2.0, 50.0, List.of(ResourceTarget.POPULATION.recurring(1)));

  // Fall
  public static final Card WORKSHOP =
      new Card(5.0, 40.0, List.of(ResourceTarget.PRODUCTION.recurring(10.0)));
  public static final Card MANOR =
      new Card(
          8.0,
          120.0,
          List.of(ResourceTarget.POPULATION.recurring(2), ResourceTarget.PRODUCTION.recurring(20)));
  public static final Card HARVEST = new Card(2.0, 10.0, List.of(ResourceTarget.FOOD.once(40)));
  public static final Card TEXTILES =
      new Card(4.0, 60.0, List.of(ResourceTarget.PRODUCTION.recurring(20.0)));

  // Winter
  public static final Card FORGE =
      new Card(3.0, 50.0, List.of(ResourceTarget.PRODUCTION.recurring(15)));
  public static final Card WOODPILES =
      new Card(1.0, 10.0, List.of(ResourceTarget.PRODUCTION.once(25)));
  public static final Card GRANARY =
      new Card(
          2.0, 40.0, List.of(ResourceTarget.FOOD.recurring(10.0), ResourceTarget.FOOD.once(20)));

  // ----- Default catalog factory -----

  /** Builds the canonical King's Hand card catalog with all cards assigned to their season. */
  public static CardCatalog defaultCatalog() {
    Map<Season, List<Card>> seasonal = new EnumMap<>(Season.class);

    seasonal.put(Season.SPRING, List.of(FARM, SEEDS, FERTILIZER, MINE));

    seasonal.put(
        Season.SUMMER,
        List.of(
            FARMSTEAD, HUNTING, SETTLEMENT, LOGGING, TRADE_ROUTES, FISHING_VILLAGE, SCHOOL_HOUSE));

    seasonal.put(Season.FALL, List.of(WORKSHOP, MANOR, HARVEST, TEXTILES));

    seasonal.put(Season.WINTER, List.of(FORGE, WOODPILES, GRANARY));

    return new CardCatalog(seasonal);
  }
}
