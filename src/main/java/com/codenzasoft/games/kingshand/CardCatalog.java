package com.codenzasoft.games.kingshand;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
  private final Map<Season, List<Card>> greatWorksBySeason;
  private final Map<String, Card> byName;

  public CardCatalog(Map<Season, List<Card>> seasonalCards) {
    Map<Season, List<Card>> copy = new EnumMap<>(Season.class);
    for (Season s : Season.values()) {
      List<Card> cards = seasonalCards == null ? null : seasonalCards.get(s);
      copy.put(s, cards == null ? List.of() : List.copyOf(cards));
    }
    this.seasonalCards = Collections.unmodifiableMap(copy);

    // Index all great-work construction cards by season and by name.
    Map<Season, List<Card>> gwBySeasonMutable = new EnumMap<>(Season.class);
    for (Season s : Season.values()) gwBySeasonMutable.put(s, new java.util.ArrayList<>());
    Map<String, Card> byNameMutable = new HashMap<>();
    for (List<Card> cards : copy.values()) {
      for (Card c : cards) {
        byNameMutable.put(c.name(), c);
        if (c.isGreatWork() && c.tableSlot() != null) {
          gwBySeasonMutable.get(c.tableSlot()).add(c);
        }
      }
    }
    // Also index the standalone great-work construction cards (not in the seasonal ADD pool).
    for (Card gw : ALL_GREAT_WORKS) {
      byNameMutable.put(gw.name(), gw);
      if (gw.tableSlot() != null) gwBySeasonMutable.get(gw.tableSlot()).add(gw);
    }
    for (Season s : Season.values()) {
      gwBySeasonMutable.put(s, Collections.unmodifiableList(gwBySeasonMutable.get(s)));
    }
    this.greatWorksBySeason = Collections.unmodifiableMap(gwBySeasonMutable);
    this.byName = Collections.unmodifiableMap(byNameMutable);
  }

  /** All cards available for offer during {@code season} (non-great-work cards only). */
  public List<Card> forSeason(Season season) {
    if (season == null) {
      throw new IllegalArgumentException("season cannot be null");
    }
    return seasonalCards.get(season);
  }

  /** Flat list of every seasonal card across all seasons, in season order (SPRING → WINTER). */
  public List<Card> all() {
    return seasonalCards.values().stream().flatMap(List::stream).toList();
  }

  /**
   * All Great Work under-construction cards for {@code season} (i.e. cards that can be commissioned
   * when the current season matches {@code season}).
   */
  public List<Card> greatWorksForSeason(Season season) {
    return greatWorksBySeason.getOrDefault(season, List.of());
  }

  /**
   * Finds any card — seasonal or Great Work — by its stable name. Returns {@code null} if not
   * found.
   */
  public Card getByName(String name) {
    return byName.get(name);
  }

  // ----- Card constants -----

  // Spring
  public static final Card BUNKHOUSE =
      new Card(
          "BUNKHOUSE",
          5.0,
          20.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.POPULATION_CAP.integer(5)));
  public static final Card FARM =
      new Card(
          "FARM", 5.0, 30.0, FrequencyType.RECURRING, List.of(ResourceTarget.FOOD.integer(10)));
  public static final Card SEEDS =
      new Card("SEEDS", 3.0, 10.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(15)));
  public static final Card FERTILIZER =
      new Card(
          "FERTILIZER",
          5.0,
          30.0,
          FrequencyType.RECURRING,
          List.of(new Effect(new CardTarget(ResourceTarget.FOOD, true), 3.0, ValueType.INTEGER)));
  public static final Card MINE =
      new Card(
          "MINE",
          10.0,
          60.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(25)));
  public static final Card HUNTING_CAMP =
      new Card(
          "HUNTING_CAMP",
          15.0,
          80.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(12), ResourceTarget.PRODUCTION.integer(16)));
  public static final Card SETTLERS =
      new Card(
          "SETTLERS",
          20.0,
          10.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.POPULATION.integer(25)));
  public static final Card LUMBER_YARD =
      new Card(
          "LUMBER_YARD",
          15.0,
          60.0,
          FrequencyType.RECURRING,
          List.of(
              new Effect(
                  new CardTarget(ResourceTarget.PRODUCTION, false), 2.0, ValueType.INTEGER)));
  public static final Card COMMUNITY_WELL =
      new Card(
          "COMMUNITY_WELL",
          12.0,
          50.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(10), ResourceTarget.POPULATION_CAP.integer(3)));
  public static final Card IRRIGATION =
      new Card(
          "IRRIGATION",
          15.0,
          75.0,
          FrequencyType.SINGLE,
          List.of(new Effect(new CardTarget(ResourceTarget.FOOD, false), 10.0, ValueType.INTEGER)));

  // Summer
  public static final Card FARMSTEAD =
      new Card(
          "FARMSTEAD",
          10.0,
          50.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(8), ResourceTarget.PRODUCTION.integer(16)));
  public static final Card THE_HUNT =
      new Card(
          "THE_HUNT", 4.0, 20.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(30)));
  public static final Card LOGGING =
      new Card(
          "LOGGING",
          15.0,
          20.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.PRODUCTION.integer(80)));
  public static final Card TRADE_ROUTES =
      new Card(
          "TRADE_ROUTES",
          10.0,
          40.0,
          FrequencyType.RECURRING,
          List.of(
              new Effect(new CardTarget(ResourceTarget.PRODUCTION, false), 1.0, ValueType.INTEGER),
              new Effect(new CardTarget(ResourceTarget.FOOD, false), 1.0, ValueType.INTEGER)));
  public static final Card FISHING_VILLAGE =
      new Card(
          "FISHING_VILLAGE",
          12.0,
          60,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(15), ResourceTarget.PRODUCTION.integer(5)));
  public static final Card SCHOOL_HOUSE =
      new Card(
          "SCHOOL_HOUSE",
          15.0,
          60.0,
          FrequencyType.SINGLE,
          List.of(
              new Effect(
                  new CardTarget(ResourceTarget.PRODUCTION, false), 10.0, ValueType.INTEGER)));
  public static final Card EARLY_HARVEST =
      new Card(
          "EARLY_HARVEST",
          5.0,
          40.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.FOOD.integer(35)));
  public static final Card WINDMILL =
      new Card(
          "WINDMILL",
          8.0,
          60.0,
          FrequencyType.RECURRING,
          List.of(new Effect(new CardTarget(ResourceTarget.FOOD, true), 5.0, ValueType.INTEGER)));
  public static final Card MARKETPLACE =
      new Card(
          "MARKETPLACE",
          15.0,
          70.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(10), ResourceTarget.PRODUCTION.integer(10)));
  public static final Card CARAVAN =
      new Card(
          "CARAVAN",
          10.0,
          30.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(5), ResourceTarget.PRODUCTION.integer(5)));
  public static final Card TOWN_FAIR =
      new Card(
          "TOWN_FAIR",
          5.0,
          40.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.POPULATION_CAP.integer(10), ResourceTarget.FOOD.integer(30)));
  public static final Card STONE_QUARRY =
      new Card(
          "STONE_QUARRY",
          10.0,
          55.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(20)));
  public static final Card MERCHANT_GUILD =
      new Card(
          "MERCHANT_GUILD",
          20.0,
          125.0,
          FrequencyType.RECURRING,
          List.of(
              new Effect(new CardTarget(ResourceTarget.PRODUCTION, false), 5.0, ValueType.INTEGER),
              new Effect(new CardTarget(ResourceTarget.FOOD, false), 5.0, ValueType.INTEGER)));

  // Fall
  public static final Card WORKSHOP =
      new Card(
          "WORKSHOP",
          8.0,
          40.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(15)));
  public static final Card MANOR =
      new Card(
          "MANOR",
          15.0,
          45.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.POPULATION_CAP.integer(15)));
  public static final Card HARVEST =
      new Card(
          "HARVEST", 10.0, 15.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(40)));
  public static final Card TEXTILES =
      new Card(
          "TEXTILES",
          12.0,
          60.0,
          FrequencyType.RECURRING,
          List.of(
              new Effect(new CardTarget(ResourceTarget.PRODUCTION, true), 5.0, ValueType.INTEGER)));
  public static final Card BAKERY =
      new Card(
          "BAKERY",
          12.0,
          60.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.FOOD.integer(12),
              new Effect(new CardTarget(ResourceTarget.FOOD, false), 1.0, ValueType.INTEGER)));
  public static final Card TAX_COLLECTION =
      new Card(
          "TAX_COLLECTION",
          10.0,
          10.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.PRODUCTION.integer(80), ResourceTarget.POPULATION.integer(-12)));
  public static final Card STOREHOUSE =
      new Card(
          "STOREHOUSE",
          14.0,
          65.0,
          FrequencyType.RECURRING,
          List.of(
              new Effect(new CardTarget(ResourceTarget.PRODUCTION, true), 4.0, ValueType.INTEGER),
              new Effect(new CardTarget(ResourceTarget.FOOD, true), 4.0, ValueType.INTEGER)));
  public static final Card BARN =
      new Card(
          "BARN", 18.0, 50.0, FrequencyType.RECURRING, List.of(ResourceTarget.FOOD.integer(25)));
  public static final Card MASON_GUILD =
      new Card(
          "MASON_GUILD",
          16.0,
          90.0,
          FrequencyType.SINGLE,
          List.of(
              new Effect(
                  new CardTarget(ResourceTarget.PRODUCTION, false), 12.0, ValueType.INTEGER)));
  public static final Card METALWORKING =
      new Card(
          "METALWORKING",
          15.0,
          50.0,
          FrequencyType.RECURRING,
          List.of(
              new Effect(
                  new CardTarget(ResourceTarget.PRODUCTION, false), 2.0, ValueType.INTEGER)));
  public static final Card BLACKSMITH =
      new Card(
          "BLACKSMITH",
          22.0,
          150.0,
          FrequencyType.RECURRING,
          List.of(
              new Effect(new CardTarget(ResourceTarget.PRODUCTION, true), 9.0, ValueType.INTEGER)));

  // Winter
  public static final Card FORGE =
      new Card(
          "FORGE",
          12.0,
          55.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(25)));
  public static final Card WOODPILES =
      new Card(
          "WOODPILES",
          10.0,
          10.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.PRODUCTION.integer(50)));
  public static final Card GRANARY =
      new Card(
          "GRANARY", 10.0, 20.0, FrequencyType.RECURRING, List.of(ResourceTarget.FOOD.integer(10)));
  public static final Card EMERGENCY_RATIONS =
      new Card(
          "EMERGENCY_RATIONS",
          5.0,
          25.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.FOOD.integer(25)));
  public static final Card STOCKPILE =
      new Card(
          "STOCKPILE",
          40.0,
          75.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(40), ResourceTarget.FOOD.integer(40)));
  public static final Card CHAPEL =
      new Card(
          "CHAPEL",
          15.0,
          40.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(35), ResourceTarget.POPULATION_CAP.integer(3)));
  public static final Card PRAY =
      new Card(
          "PRAY",
          3.0,
          1.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.FOOD.integer(5), ResourceTarget.PRODUCTION.integer(5)));
  public static final Card DESPERATE_TIMES =
      new Card(
          "DESPERATE_TIMES",
          11.0,
          10.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(30), ResourceTarget.POPULATION.integer(-10)));

  // ----- Gold & Strength resource cards (unlocked by TREASURY / BARRACKS great works) -----

  // Spring: one gold, one strength
  public static final Card GOLD_MARKET =
      new Card(
          "GOLD_MARKET",
          10.0,
          80.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.GOLD.integer(8), ResourceTarget.PRODUCTION.integer(5)));
  public static final Card TRAINING_GROUNDS =
      new Card(
          "TRAINING_GROUNDS",
          12.0,
          90.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.STRENGTH.integer(10)));

  // Summer: one gold, one strength
  public static final Card TRADE_CONVOY =
      new Card(
          "TRADE_CONVOY",
          15.0,
          100.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.GOLD.integer(12), ResourceTarget.PRODUCTION.integer(8)));
  public static final Card GUARD_POST =
      new Card(
          "GUARD_POST",
          14.0,
          85.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.STRENGTH.integer(8), ResourceTarget.FOOD.integer(-5)));

  // Fall: one gold, one strength
  public static final Card GOLD_MINE =
      new Card(
          "GOLD_MINE",
          18.0,
          140.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.GOLD.integer(20)));
  public static final Card ARMORY =
      new Card(
          "ARMORY",
          16.0,
          120.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.STRENGTH.integer(15)));

  // Winter: one gold, one strength
  public static final Card MINT =
      new Card(
          "MINT", 20.0, 160.0, FrequencyType.RECURRING, List.of(ResourceTarget.GOLD.integer(25)));
  public static final Card FORTRESS_WALL =
      new Card(
          "FORTRESS_WALL",
          18.0,
          130.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.STRENGTH.integer(18)));

  // ----- Great Work cards -----
  // Completed cards are defined first because construction cards reference them.

  public static final Card TREASURY_COMPLETED =
      new Card(
          "TREASURY", 15.0, 0.0, FrequencyType.RECURRING, List.of(ResourceTarget.GOLD.integer(25)));

  public static final Card BARRACKS_COMPLETED =
      new Card(
          "BARRACKS",
          15.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.STRENGTH.integer(20), ResourceTarget.FOOD.integer(10)));

  // Win-condition completed cards carry winCondition=true so the engine can detect victory by
  // scanning tables for cards that are (winCondition && !isGreatWork()).
  public static final Card THE_GREAT_RESERVOIR_COMPLETED =
      new Card(
          "THE_GREAT_RESERVOIR",
          0.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(100), ResourceTarget.PRODUCTION.integer(50)),
          0,
          null,
          Set.of(),
          true,
          null);

  public static final Card THE_GREAT_ROAD_COMPLETED =
      new Card(
          "THE_GREAT_ROAD",
          0.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.FOOD.integer(100),
              ResourceTarget.PRODUCTION.integer(80),
              ResourceTarget.GOLD.integer(50)),
          0,
          null,
          Set.of(),
          true,
          null);

  public static final Card THE_GREAT_CAPITAL_COMPLETED =
      new Card(
          "THE_GREAT_CAPITAL",
          0.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.FOOD.integer(200),
              ResourceTarget.PRODUCTION.integer(120),
              ResourceTarget.GOLD.integer(80),
              ResourceTarget.STRENGTH.integer(20)),
          0,
          null,
          Set.of(),
          true,
          null);

  public static final Card THE_GREAT_CONQUEST_COMPLETED =
      new Card(
          "THE_GREAT_CONQUEST",
          0.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.FOOD.integer(300),
              ResourceTarget.PRODUCTION.integer(200),
              ResourceTarget.GOLD.integer(200),
              ResourceTarget.STRENGTH.integer(100)),
          0,
          null,
          Set.of(),
          true,
          null);

  // Under-construction cards — carry all greatwork metadata.
  public static final Card TREASURY_CONSTRUCTION =
      new Card(
          "TREASURY_CONSTRUCTION",
          25.0,
          300.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(-50)),
          4,
          TREASURY_COMPLETED,
          Set.of(ResourceTarget.GOLD),
          false,
          Season.SUMMER);

  public static final Card BARRACKS_CONSTRUCTION =
      new Card(
          "BARRACKS_CONSTRUCTION",
          22.0,
          250.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(-40), ResourceTarget.FOOD.integer(-20)),
          3,
          BARRACKS_COMPLETED,
          Set.of(ResourceTarget.STRENGTH),
          false,
          Season.FALL);

  public static final Card THE_GREAT_RESERVOIR_CONSTRUCTION =
      new Card(
          "THE_GREAT_RESERVOIR_CONSTRUCTION",
          20.0,
          450.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-35),
              ResourceTarget.FOOD.integer(-25)),
          6,
          THE_GREAT_RESERVOIR_COMPLETED,
          Set.of(),
          true,
          Season.SPRING);

  public static final Card THE_GREAT_ROAD_CONSTRUCTION =
      new Card(
          "THE_GREAT_ROAD_CONSTRUCTION",
          25.0,
          550.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-50),
              ResourceTarget.FOOD.integer(-30),
              ResourceTarget.GOLD.integer(-15)),
          8,
          THE_GREAT_ROAD_COMPLETED,
          Set.of(),
          true,
          Season.SUMMER);

  public static final Card THE_GREAT_CAPITAL_CONSTRUCTION =
      new Card(
          "THE_GREAT_CAPITAL_CONSTRUCTION",
          30.0,
          700.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-70),
              ResourceTarget.FOOD.integer(-35),
              ResourceTarget.GOLD.integer(-20),
              ResourceTarget.STRENGTH.integer(-15)),
          10,
          THE_GREAT_CAPITAL_COMPLETED,
          Set.of(),
          true,
          Season.FALL);

  public static final Card THE_GREAT_CONQUEST_CONSTRUCTION =
      new Card(
          "THE_GREAT_CONQUEST_CONSTRUCTION",
          40.0,
          900.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-100),
              ResourceTarget.FOOD.integer(-60),
              ResourceTarget.GOLD.integer(-30),
              ResourceTarget.STRENGTH.integer(-25)),
          12,
          THE_GREAT_CONQUEST_COMPLETED,
          Set.of(),
          true,
          Season.WINTER);

  /**
   * All Great Work under-construction cards, in commissioning order. Used by the catalog
   * constructor to populate the greatworks-by-season index.
   */
  public static final List<Card> ALL_GREAT_WORKS =
      List.of(
          TREASURY_CONSTRUCTION,
          BARRACKS_CONSTRUCTION,
          THE_GREAT_RESERVOIR_CONSTRUCTION,
          THE_GREAT_ROAD_CONSTRUCTION,
          THE_GREAT_CAPITAL_CONSTRUCTION,
          THE_GREAT_CONQUEST_CONSTRUCTION);

  // ----- Default catalog factory -----

  /**
   * Returns the starting deck for a new game, grouped by season. Each season receives three cards
   * that give the player a viable foundation across all resource types.
   */
  public static Map<Season, List<Card>> defaultStartingDeck() {
    Map<Season, List<Card>> deck = new EnumMap<>(Season.class);
    deck.put(Season.SPRING, List.of(MINE, SEEDS, FERTILIZER));
    deck.put(Season.SUMMER, List.of(BUNKHOUSE, EARLY_HARVEST, STONE_QUARRY, TREASURY_CONSTRUCTION));
    deck.put(Season.FALL, List.of(WORKSHOP, HARVEST, BAKERY, BARRACKS_CONSTRUCTION));
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
            IRRIGATION,
            GOLD_MARKET, // available only when GOLD unlocked
            TRAINING_GROUNDS, // available only when STRENGTH unlocked
            THE_GREAT_RESERVOIR_CONSTRUCTION // no gold/strength cost — always available
            ));

    seasonal.put(
        Season.SUMMER,
        List.of(
            BUNKHOUSE,
            MANOR,
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
            MERCHANT_GUILD,
            TRADE_CONVOY, // available only when GOLD unlocked
            GUARD_POST, // available only when STRENGTH unlocked
            THE_GREAT_ROAD_CONSTRUCTION // available only when GOLD unlocked
            ));

    seasonal.put(
        Season.FALL,
        List.of(
            WORKSHOP,
            HARVEST,
            TEXTILES,
            BAKERY,
            TAX_COLLECTION,
            STOREHOUSE,
            BARN,
            MASON_GUILD,
            METALWORKING,
            BLACKSMITH,
            GOLD_MINE, // available only when GOLD unlocked
            ARMORY, // available only when STRENGTH unlocked
            THE_GREAT_CAPITAL_CONSTRUCTION // available only when GOLD + STRENGTH unlocked
            ));

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
            DESPERATE_TIMES,
            MINT, // available only when GOLD unlocked
            FORTRESS_WALL, // available only when STRENGTH unlocked
            THE_GREAT_CONQUEST_CONSTRUCTION // available only when GOLD + STRENGTH unlocked
            ));

    return new CardCatalog(seasonal);
  }
}
