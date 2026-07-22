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
      }
    }
    // Index every Great Work construction card by season and by name. ALL_GREAT_WORKS is the
    // single authoritative list of all six Great Works, so each is indexed exactly once here —
    // even though four of them (the win-condition Great Works) are *also* physically embedded in
    // their season's seasonalCards list above (so they can appear in the ADD-phase draft pool).
    // Indexing them again from that seasonal-list scan would have double-counted those four in
    // greatWorksBySeason, so that case is intentionally skipped above.
    for (Card gw : ALL_GREAT_WORKS) {
      byNameMutable.put(gw.name(), gw);
      if (gw.season() != null) gwBySeasonMutable.get(gw.season()).add(gw);
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
          25.0,
          100.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.POPULATION_CAP.integer(25)));
  public static final Card FARM =
      new Card(
          "FARM", 25.0, 90.0, FrequencyType.RECURRING, List.of(ResourceTarget.FOOD.integer(50)));
  public static final Card SEEDS =
      new Card("SEEDS", 15.0, 50.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(75)));
  public static final Card FERTILIZER =
      new Card(
          "FERTILIZER",
          25.0,
          150.0,
          FrequencyType.RECURRING,
          List.of(new Effect(new CardTarget(ResourceTarget.FOOD, true), 15.0)));
  public static final Card MINE =
      new Card(
          "MINE",
          50.0,
          300.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(125)));
  public static final Card HUNTING_CAMP =
      new Card(
          "HUNTING_CAMP",
          75.0,
          400.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(60), ResourceTarget.PRODUCTION.integer(80)));
  public static final Card SETTLERS =
      new Card(
          "SETTLERS",
          100.0,
          50.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.POPULATION.integer(125)));
  public static final Card LUMBER_YARD =
      new Card(
          "LUMBER_YARD",
          75.0,
          300.0,
          FrequencyType.RECURRING,
          List.of(new Effect(new CardTarget(ResourceTarget.PRODUCTION, false), 10.0)));
  public static final Card COMMUNITY_WELL =
      new Card(
          "COMMUNITY_WELL",
          60.0,
          250.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(50), ResourceTarget.POPULATION_CAP.integer(15)));
  public static final Card IRRIGATION =
      new Card(
          "IRRIGATION",
          75.0,
          375.0,
          FrequencyType.SINGLE,
          List.of(new Effect(new CardTarget(ResourceTarget.FOOD, false), 50.0)));

  // Summer
  public static final Card FARMSTEAD =
      new Card(
          "FARMSTEAD",
          50.0,
          250.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(40), ResourceTarget.PRODUCTION.integer(80)));
  public static final Card THE_HUNT =
      new Card(
          "THE_HUNT", 20.0, 100.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(150)));
  public static final Card LOGGING =
      new Card(
          "LOGGING",
          75.0,
          100.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.PRODUCTION.integer(400)));
  public static final Card TRADING_POST =
      new Card(
          "TRADING_POST",
          50.0,
          200.0,
          FrequencyType.RECURRING,
          List.of(
              new Effect(new CardTarget(ResourceTarget.PRODUCTION, false), 5.0),
              new Effect(new CardTarget(ResourceTarget.FOOD, false), 5.0)));
  public static final Card FISHING_VILLAGE =
      new Card(
          "FISHING_VILLAGE",
          60.0,
          300,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(75), ResourceTarget.PRODUCTION.integer(25)));
  public static final Card SCHOOL_HOUSE =
      new Card(
          "SCHOOL_HOUSE",
          75.0,
          300.0,
          FrequencyType.SINGLE,
          List.of(new Effect(new CardTarget(ResourceTarget.PRODUCTION, false), 50.0)));
  public static final Card EARLY_HARVEST =
      new Card(
          "EARLY_HARVEST",
          25.0,
          200.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.FOOD.integer(175)));
  public static final Card WINDMILL =
      new Card(
          "WINDMILL",
          40.0,
          300.0,
          FrequencyType.RECURRING,
          List.of(new Effect(new CardTarget(ResourceTarget.FOOD, true), 25.0)));
  public static final Card CARAVAN =
      new Card(
          "CARAVAN",
          50.0,
          150.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(25), ResourceTarget.PRODUCTION.integer(25)));
  public static final Card TOWN_FAIR =
      new Card(
          "TOWN_FAIR",
          25.0,
          200.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.POPULATION_CAP.integer(50), ResourceTarget.FOOD.integer(150)));
  public static final Card STONE_QUARRY =
      new Card(
          "STONE_QUARRY",
          50.0,
          275.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(100)));
  public static final Card MERCHANT_GUILD =
      new Card(
          "MERCHANT_GUILD",
          100.0,
          625.0,
          FrequencyType.RECURRING,
          List.of(
              new Effect(new CardTarget(ResourceTarget.PRODUCTION, false), 25.0),
              new Effect(new CardTarget(ResourceTarget.FOOD, false), 25.0)));

  // Fall
  public static final Card WORKSHOP =
      new Card(
          "WORKSHOP",
          40.0,
          200.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(90)));
  public static final Card MANOR =
      new Card(
          "MANOR",
          75.0,
          225.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.POPULATION_CAP.integer(75)));
  public static final Card HARVEST =
      new Card(
          "HARVEST", 50.0, 75.0, FrequencyType.SINGLE, List.of(ResourceTarget.FOOD.integer(200)));
  public static final Card TEXTILES =
      new Card(
          "TEXTILES",
          60.0,
          300.0,
          FrequencyType.RECURRING,
          List.of(new Effect(new CardTarget(ResourceTarget.PRODUCTION, true), 25.0)));
  public static final Card BAKERY =
      new Card(
          "BAKERY",
          60.0,
          300.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.FOOD.integer(60),
              new Effect(new CardTarget(ResourceTarget.FOOD, false), 5.0)));
  public static final Card TAX_COLLECTION =
      new Card(
          "TAX_COLLECTION",
          50.0,
          50.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.GOLD.integer(400), ResourceTarget.POPULATION.integer(-60)));
  public static final Card STOREHOUSE =
      new Card(
          "STOREHOUSE",
          70.0,
          325.0,
          FrequencyType.RECURRING,
          List.of(
              new Effect(new CardTarget(ResourceTarget.PRODUCTION, true), 20.0),
              new Effect(new CardTarget(ResourceTarget.FOOD, true), 20.0)));
  public static final Card BARN =
      new Card(
          "BARN", 90.0, 250.0, FrequencyType.RECURRING, List.of(ResourceTarget.FOOD.integer(125)));
  public static final Card MASON_GUILD =
      new Card(
          "MASON_GUILD",
          80.0,
          450.0,
          FrequencyType.SINGLE,
          List.of(new Effect(new CardTarget(ResourceTarget.PRODUCTION, false), 60.0)));
  public static final Card METALWORKING =
      new Card(
          "METALWORKING",
          75.0,
          250.0,
          FrequencyType.RECURRING,
          List.of(new Effect(new CardTarget(ResourceTarget.PRODUCTION, false), 10.0)));
  public static final Card BLACKSMITH =
      new Card(
          "BLACKSMITH",
          110.0,
          750.0,
          FrequencyType.RECURRING,
          List.of(new Effect(new CardTarget(ResourceTarget.PRODUCTION, true), 45.0)));

  // Winter
  public static final Card FORGE =
      new Card(
          "FORGE",
          60.0,
          275.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(125)));
  public static final Card WOODPILES =
      new Card(
          "WOODPILES",
          50.0,
          50.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.PRODUCTION.integer(250)));
  public static final Card GRANARY =
      new Card(
          "GRANARY",
          50.0,
          100.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(50)));
  public static final Card EMERGENCY_RATIONS =
      new Card(
          "EMERGENCY_RATIONS",
          25.0,
          125.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.FOOD.integer(125)));
  public static final Card STOCKPILE =
      new Card(
          "STOCKPILE",
          200.0,
          375.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(200), ResourceTarget.FOOD.integer(200)));
  public static final Card CHAPEL =
      new Card(
          "CHAPEL",
          75.0,
          200.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(175), ResourceTarget.POPULATION_CAP.integer(15)));
  public static final Card PRAY =
      new Card(
          "PRAY",
          15.0,
          5.0,
          FrequencyType.SINGLE,
          List.of(ResourceTarget.FOOD.integer(25), ResourceTarget.PRODUCTION.integer(25)));
  public static final Card DESPERATE_TIMES =
      new Card(
          "DESPERATE_TIMES",
          55.0,
          50.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(150), ResourceTarget.POPULATION.integer(-50)));

  // ----- Gold & Strength resource cards (unlocked by TREASURY / BARRACKS great works) -----

  // Spring: one gold, one strength
  public static final Card MARKETPLACE =
      new Card(
          "MARKETPLACE",
          50.0,
          400.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.GOLD.integer(40), ResourceTarget.PRODUCTION.integer(25)));
  public static final Card TRAINING_GROUNDS =
      new Card(
          "TRAINING_GROUNDS",
          60.0,
          450.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.STRENGTH.integer(50)));

  // Summer: one gold, one strength
  public static final Card TRADE_ROUTES =
      new Card(
          "TRADE_ROUTES",
          75.0,
          500.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.GOLD.integer(60), ResourceTarget.PRODUCTION.integer(40)));
  public static final Card GUARD_POST =
      new Card(
          "GUARD_POST",
          70.0,
          425.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.STRENGTH.integer(40), ResourceTarget.FOOD.integer(-25)));

  // Fall: one gold, one strength
  public static final Card GOLD_MINE =
      new Card(
          "GOLD_MINE",
          90.0,
          700.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.GOLD.integer(100)));
  public static final Card ARMORY =
      new Card(
          "ARMORY",
          80.0,
          600.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.STRENGTH.integer(75)));

  // Winter: one gold, one strength
  public static final Card FOREIGN_AID =
      new Card(
          "FOREIGN_AID",
          100.0,
          800.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.GOLD.integer(125)));
  public static final Card FORTRESS_WALL =
      new Card(
          "FORTRESS_WALL",
          90.0,
          650.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.STRENGTH.integer(90)));

  // ----- Sabotage cards (cross-seat effects; require STRENGTH unlocked) -----
  //
  // Each sabotage card spends the attacker's own STRENGTH (a normal ResourceTarget cost, so the
  // card is gated behind the BARRACKS unlock and checked by canAfford) and carries an
  // OpponentResourceTarget effect that drains the opposing kingdom. Sabotage resolves against a
  // pre-resolution snapshot of the opponent — see GameEngine#resolvePlayForAllSeats.

  /** Summer: a one-shot raid on the opponent's workshops — drains their production. */
  public static final Card SABOTEURS =
      new Card(
          "SABOTEURS",
          40.0,
          350.0,
          FrequencyType.SINGLE,
          List.of(
              ResourceTarget.STRENGTH.integer(-25),
              new Effect(new OpponentResourceTarget(ResourceTarget.PRODUCTION), -200)));

  /** Fall: a one-shot raid on the opponent's granaries — burns their food stores. */
  public static final Card CROP_RAID =
      new Card(
          "CROP_RAID",
          50.0,
          300.0,
          FrequencyType.SINGLE,
          List.of(
              ResourceTarget.STRENGTH.integer(-30),
              new Effect(new OpponentResourceTarget(ResourceTarget.FOOD), -250)));

  /** Winter: recurring raiders that harry the opponent's economy every winter. */
  public static final Card WINTER_RAIDERS =
      new Card(
          "WINTER_RAIDERS",
          80.0,
          550.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.STRENGTH.integer(-40),
              new Effect(new OpponentResourceTarget(ResourceTarget.PRODUCTION), -100),
              new Effect(new OpponentResourceTarget(ResourceTarget.FOOD), -100)));

  // ----- Great Work cards -----
  // Completed cards are defined first because construction cards reference them.

  public static final Card TREASURY_COMPLETED =
      new Card(
          "TREASURY",
          75.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.GOLD.integer(40), ResourceTarget.PRODUCTION.integer(125)));

  public static final Card BARRACKS_COMPLETED =
      new Card(
          "BARRACKS",
          75.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.STRENGTH.integer(75), ResourceTarget.PRODUCTION.integer(200)));

  // Win-condition completed cards carry victory=true so the engine can detect victory by
  // scanning tables for cards that are (victory && !isGreatWork()).
  public static final Card THE_GREAT_RESERVOIR_COMPLETED =
      new Card(
          "THE_GREAT_RESERVOIR",
          0.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.FOOD.integer(1100), ResourceTarget.PRODUCTION.integer(600)),
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
              ResourceTarget.FOOD.integer(1200),
              ResourceTarget.PRODUCTION.integer(800),
              ResourceTarget.GOLD.integer(200)),
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
              ResourceTarget.FOOD.integer(2200),
              ResourceTarget.PRODUCTION.integer(1200),
              ResourceTarget.GOLD.integer(500),
              ResourceTarget.STRENGTH.integer(300)),
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
              ResourceTarget.FOOD.integer(6000),
              ResourceTarget.PRODUCTION.integer(4000),
              ResourceTarget.GOLD.integer(3000),
              ResourceTarget.STRENGTH.integer(3000)),
          0,
          null,
          Set.of(),
          true,
          null);

  // Under-construction cards — carry all greatwork metadata.
  public static final Card TREASURY_CONSTRUCTION =
      new Card(
          "TREASURY_CONSTRUCTION",
          100.0,
          250.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(-125)),
          2,
          TREASURY_COMPLETED,
          Set.of(ResourceTarget.GOLD),
          false,
          Season.SUMMER);

  public static final Card BARRACKS_CONSTRUCTION =
      new Card(
          "BARRACKS_CONSTRUCTION",
          150.0,
          250.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-200),
              ResourceTarget.FOOD.integer(-100),
              ResourceTarget.POPULATION.integer(-20)),
          4,
          BARRACKS_COMPLETED,
          Set.of(ResourceTarget.STRENGTH),
          false,
          Season.FALL);

  public static final Card THE_GREAT_RESERVOIR_CONSTRUCTION =
      new Card(
          "THE_GREAT_RESERVOIR_CONSTRUCTION",
          300.0,
          500.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-300),
              ResourceTarget.FOOD.integer(-150),
              ResourceTarget.POPULATION_CAP.integer(25)),
          6,
          THE_GREAT_RESERVOIR_COMPLETED,
          Set.of(),
          true,
          Season.SPRING);

  public static final Card THE_GREAT_ROAD_CONSTRUCTION =
      new Card(
          "THE_GREAT_ROAD_CONSTRUCTION",
          400.0,
          1000,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-325),
              ResourceTarget.FOOD.integer(-225),
              ResourceTarget.GOLD.integer(-150),
              ResourceTarget.POPULATION_CAP.integer(50)),
          6,
          THE_GREAT_ROAD_COMPLETED,
          Set.of(),
          true,
          Season.SUMMER);

  public static final Card THE_GREAT_CAPITAL_CONSTRUCTION =
      new Card(
          "THE_GREAT_CAPITAL_CONSTRUCTION",
          600.0,
          2000.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-350),
              ResourceTarget.FOOD.integer(-500),
              ResourceTarget.GOLD.integer(-250),
              ResourceTarget.STRENGTH.integer(-125),
              ResourceTarget.POPULATION_CAP.integer(70)),
          10,
          THE_GREAT_CAPITAL_COMPLETED,
          Set.of(),
          true,
          Season.FALL);

  public static final Card THE_GREAT_CONQUEST_CONSTRUCTION =
      new Card(
          "THE_GREAT_CONQUEST_CONSTRUCTION",
          1500.0,
          5000.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-500),
              ResourceTarget.FOOD.integer(-1000),
              ResourceTarget.GOLD.integer(-750),
              ResourceTarget.STRENGTH.integer(-500),
              ResourceTarget.POPULATION_CAP.integer(100),
              ResourceTarget.POPULATION.integer(-100)),
          10,
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
            MARKETPLACE, // available only when GOLD unlocked
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
            TRADING_POST,
            FISHING_VILLAGE,
            SCHOOL_HOUSE,
            EARLY_HARVEST,
            WINDMILL,
            CARAVAN,
            TOWN_FAIR,
            STONE_QUARRY,
            MERCHANT_GUILD,
            TRADE_ROUTES, // available only when GOLD unlocked
            GUARD_POST, // available only when STRENGTH unlocked
            SABOTEURS, // sabotage — available only when STRENGTH unlocked
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
            CROP_RAID, // sabotage — available only when STRENGTH unlocked
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
            FOREIGN_AID, // available only when GOLD unlocked
            FORTRESS_WALL, // available only when STRENGTH unlocked
            WINTER_RAIDERS, // sabotage — available only when STRENGTH unlocked
            THE_GREAT_CONQUEST_CONSTRUCTION // available only when GOLD + STRENGTH unlocked
            ));

    return new CardCatalog(seasonal);
  }
}
