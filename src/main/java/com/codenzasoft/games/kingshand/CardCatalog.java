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
  public static final Card FARM =
      new Card("FARM", 5.0, 30.0, FrequencyType.RECURRING, List.of(ResourceTarget.FOOD.integer(10)));
  public static final Card SEEDS =
      new Card("SEEDS", 1.0, 15.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(10)));
  public static final Card FERTILIZER =
      new Card("FERTILIZER", 1.0, 30.0, FrequencyType.RECURRING, List.of(ResourceTarget.FOOD.percentage(10.0)));
  public static final Card MINE =
      new Card("MINE", 10.0, 60.0, FrequencyType.RECURRING, List.of(ResourceTarget.PRODUCTION.integer(15)));
  public static final Card HUNTING_CAMP =
      new Card(
          "HUNTING_CAMP",
          30.0,
          80.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(12), ResourceTarget.PRODUCTION.integer(8)));
  public static final Card SETTLERS =
      new Card("SETTLERS", 50.0, 50.0, FrequencyType.SINGLE, List.of(ResourceTarget.POPULATION.integer(25)));
  public static final Card LUMBER_YARD =
      new Card(
          "LUMBER_YARD",
          40.0,
          80.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(20), ResourceTarget.PRODUCTION.percentage(5)));
  public static final Card COMMUNITY_WELL =
      new Card(
          "COMMUNITY_WELL",
          100.0,
          50.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(10), ResourceTarget.POPULATION.percentage(10)));
  public static final Card IRRIGATION =
      new Card(
          "IRRIGATION",
          100.0,
          120.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.percentage(15.0), ResourceTarget.FOOD.integer(15)));

  // Summer
  public static final Card FARMSTEAD =
      new Card(
          "FARMSTEAD",
          20.0,
          50.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(8), ResourceTarget.PRODUCTION.integer(8)));
  public static final Card THE_HUNT =
      new Card("THE_HUNT", 15.0, 20.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(30)));
  public static final Card LOGGING =
      new Card("LOGGING", 50.0, 20.0, FrequencyType.SINGLE, List.of(ResourceTarget.PRODUCTION.integer(40)));
  public static final Card TRADE_ROUTES =
      new Card(
          "TRADE_ROUTES",
          80.0,
          80.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.percentage(5.0), ResourceTarget.FOOD.percentage(5.0)));
  public static final Card FISHING_VILLAGE =
      new Card("FISHING_VILLAGE", 40.0, 60, FrequencyType.RECURRING, List.of(ResourceTarget.FOOD.integer(15)));
  public static final Card SCHOOL_HOUSE =
      new Card(
          "SCHOOL_HOUSE",
          100.0,
          75.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.percentage(8), ResourceTarget.FOOD.percentage(5)));
  public static final Card EARLY_HARVEST =
      new Card("EARLY_HARVEST", 10.0, 30.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(35)));
  public static final Card WINDMILL =
      new Card(
          "WINDMILL",
          2.0,
          70.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.FOOD.integer(10),
              ResourceTarget.FOOD.percentage(5),
              ResourceTarget.PRODUCTION.integer(5)));
  public static final Card MARKETPLACE =
      new Card(
          "MARKETPLACE",
          80.0,
          70.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.percentage(10.0), ResourceTarget.FOOD.integer(10)));
  public static final Card CARAVAN =
      new Card(
          "CARAVAN",
          80.0,
          70.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(10), ResourceTarget.FOOD.percentage(10)));
  public static final Card TOWN_FAIR =
      new Card(
          "TOWN_FAIR",
          30.0,
          40.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.POPULATION.integer(10), ResourceTarget.FOOD.integer(20)));
  public static final Card STONE_QUARRY =
      new Card(
          "STONE_QUARRY",
          10.0,
          60.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(15), ResourceTarget.PRODUCTION.percentage(5)));
  public static final Card MERCHANT_GUILD =
      new Card(
          "MERCHANT_GUILD",
          150.0,
          120.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.percentage(10.0), ResourceTarget.FOOD.percentage(10.0)));

  // Fall
  public static final Card WORKSHOP =
      new Card("WORKSHOP", 10.0, 40.0, FrequencyType.RECURRING, List.of(ResourceTarget.PRODUCTION.integer(10)));
  public static final Card MANOR =
      new Card(
          "MANOR",
          80.0,
          75.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.POPULATION.integer(5), ResourceTarget.PRODUCTION.integer(20)));
  public static final Card HARVEST =
      new Card("HARVEST", 5.0, 20.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(40)));
  public static final Card TEXTILES =
      new Card(
          "TEXTILES",
          25.0,
          60.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.percentage(5.0), ResourceTarget.PRODUCTION.integer(10)));
  public static final Card BAKERY =
      new Card(
          "BAKERY",
          30.0,
          60.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(12), ResourceTarget.FOOD.percentage(5)));
  public static final Card TAX_COLLECTION =
      new Card(
          "TAX_COLLECTION",
          100.0,
          0.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.PRODUCTION.integer(40), ResourceTarget.POPULATION.integer(-10)));
  public static final Card STOREHOUSE =
      new Card("STOREHOUSE", 40.0, 45.0, FrequencyType.RECURRING, List.of(ResourceTarget.FOOD.percentage(8.0)));
  public static final Card BARN =
      new Card("BARN", 70.0, 40.0, FrequencyType.RECURRING, List.of(ResourceTarget.FOOD.integer(20)));
  public static final Card MASON_GUILD =
      new Card(
          "MASON_GUILD",
          100.0,
          100.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(25), ResourceTarget.PRODUCTION.percentage(10)));
  public static final Card STONE_TOOLS =
      new Card(
          "STONE_TOOLS", 10.0, 40.0, FrequencyType.RECURRING, List.of(ResourceTarget.PRODUCTION.percentage(5)));
  public static final Card METALWORKING =
      new Card(
          "METALWORKING", 50.0, 80.0, FrequencyType.RECURRING, List.of(ResourceTarget.PRODUCTION.percentage(10)));
  public static final Card BLACKSMITH =
      new Card(
          "BLACKSMITH", 150.0, 160.0, FrequencyType.RECURRING, List.of(ResourceTarget.PRODUCTION.percentage(20)));

  // Winter
  public static final Card FORGE =
      new Card("FORGE", 20.0, 50.0, FrequencyType.RECURRING, List.of(ResourceTarget.PRODUCTION.integer(15)));
  public static final Card WOODPILES =
      new Card("WOODPILES", 10.0, 10.0, FrequencyType.SINGLE, List.of(ResourceTarget.PRODUCTION.integer(25)));
  public static final Card GRANARY =
      new Card(
          "GRANARY",
          50.0,
          100.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.percentage(10.0), ResourceTarget.FOOD.integer(15)));
  public static final Card EMERGENCY_RATIONS =
      new Card("EMERGENCY_RATIONS", 10.0, 25.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(25)));
  public static final Card STOCKPILE =
      new Card(
          "STOCKPILE",
          30.0,
          65.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.PRODUCTION.integer(15), ResourceTarget.FOOD.percentage(5)));
  public static final Card CHAPEL =
      new Card(
          "CHAPEL",
          35.0,
          40.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(40), ResourceTarget.POPULATION.percentage(20)));
  public static final Card PRAY =
      new Card(
          "PRAY",
          1.0,
          1.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.FOOD.integer(5), ResourceTarget.PRODUCTION.integer(5)));
  public static final Card DESPERATE_TIMES =
      new Card(
          "DESPERATE_TIMES",
          10.0,
          10.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(40), ResourceTarget.POPULATION.integer(-10)));

  // ----- Default catalog factory -----

  /**
   * Returns the starting deck for a new game, grouped by season. Each season receives three cards
   * that give the player a viable foundation across all resource types.
   */
  public static Map<Season, List<Card>> defaultStartingDeck() {
    Map<Season, List<Card>> deck = new EnumMap<>(Season.class);
    deck.put(Season.SPRING, List.of(MINE, SEEDS, FERTILIZER));
    deck.put(Season.SUMMER, List.of(THE_HUNT, EARLY_HARVEST, STONE_QUARRY));
    deck.put(Season.FALL, List.of(WORKSHOP, HARVEST, BAKERY));
    deck.put(Season.WINTER, List.of(EMERGENCY_RATIONS, FORGE, PRAY));
    return Collections.unmodifiableMap(deck);
  }

  /** Builds the canonical King's Hand card catalog with all cards assigned to their season. */
  public static CardCatalog defaultCatalog() {
    Map<Season, List<Card>> seasonal = new EnumMap<>(Season.class);

    seasonal.put(
        Season.SPRING,
        List.of(
            FARM,
            SEEDS,
            FERTILIZER,
            MINE,
            HUNTING_CAMP,
            SETTLERS,
            LUMBER_YARD,
            COMMUNITY_WELL,
            IRRIGATION));

    seasonal.put(
        Season.SUMMER,
        List.of(
            FARMSTEAD,
            THE_HUNT,
            LOGGING,
            TRADE_ROUTES,
            FISHING_VILLAGE,
            SCHOOL_HOUSE,
            EARLY_HARVEST,
            WINDMILL,
            MARKETPLACE,
            CARAVAN,
            TOWN_FAIR,
            STONE_QUARRY,
            MERCHANT_GUILD));

    seasonal.put(
        Season.FALL,
        List.of(
            WORKSHOP,
            MANOR,
            HARVEST,
            TEXTILES,
            BAKERY,
            TAX_COLLECTION,
            STOREHOUSE,
            BARN,
            MASON_GUILD,
            STONE_TOOLS,
            METALWORKING,
            BLACKSMITH));

    seasonal.put(
        Season.WINTER,
        List.of(
            FORGE,
            WOODPILES,
            GRANARY,
            EMERGENCY_RATIONS,
            STOCKPILE,
            CHAPEL,
            PRAY,
            DESPERATE_TIMES));

    return new CardCatalog(seasonal);
  }
}
