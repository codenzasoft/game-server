package com.codenzasoft.games.kingshand;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catalog of all {@link GreatWork} definitions available in a King's Hand game.
 *
 * <p>Great Works are grouped into two categories:
 *
 * <ul>
 *   <li><b>Resource unlockers</b> — TREASURY (Summer, unlocks {@link ResourceTarget#GOLD}) and
 *       BARRACKS (Fall, unlocks {@link ResourceTarget#STRENGTH}). These do not contribute to the
 *       win condition but are prerequisites for building win-condition Great Works.
 *   <li><b>Win-condition Great Works</b> — one per season (Spring, Summer, Fall, Winter), scaling
 *       in difficulty. Completing all four ends the game in victory.
 * </ul>
 *
 * <p>All construction costs are expressed as negative-modifier {@link Effect}s on the
 * {@link GreatWork#underConstructionCard()}. The one-time commissioning fee is the card's
 * {@link Card#productionRequirement()}. Per-season construction checks succeed only when the
 * player has sufficient resources to cover every negative effect.
 *
 * <p>Use {@link #defaultCatalog()} to obtain the canonical catalog, or pass a custom map to the
 * constructor for tests or variant game modes.
 */
public class GreatWorkCatalog {

  private final Map<String, GreatWork> catalog;

  public GreatWorkCatalog(Collection<GreatWork> greatWorks) {
    Map<String, GreatWork> map = new LinkedHashMap<>();
    if (greatWorks != null) {
      for (GreatWork gw : greatWorks) {
        map.put(gw.name(), gw);
      }
    }
    this.catalog = Collections.unmodifiableMap(map);
  }

  /** Returns the {@link GreatWork} with the given name, or {@code null} if not found. */
  public GreatWork get(String name) {
    return catalog.get(name);
  }

  /** All Great Work definitions in insertion order. */
  public Collection<GreatWork> all() {
    return catalog.values();
  }

  /**
   * Finds a {@link GreatWork} whose {@link GreatWork#underConstructionCard()} has a name matching
   * {@code cardName}. Used during table resolution to identify active construction cards.
   *
   * @return the matching {@link GreatWork}, or {@code null} if no construction card matches
   */
  public GreatWork findByConstructionCardName(String cardName) {
    if (cardName == null) return null;
    for (GreatWork gw : catalog.values()) {
      if (gw.underConstructionCard().name().equals(cardName)) {
        return gw;
      }
    }
    return null;
  }

  // ── Under-construction card constants ─────────────────────────────────────

  // TREASURY — Summer table, unlocks GOLD
  public static final Card TREASURY_CONSTRUCTION =
      new Card(
          "TREASURY_CONSTRUCTION",
          25.0,  // staff requirement (workers at the construction site)
          300.0, // one-time commission cost in production
          FrequencyType.RECURRING,
          List.of(ResourceTarget.PRODUCTION.integer(-50)));

  public static final Card TREASURY_COMPLETED =
      new Card(
          "TREASURY",
          15.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(ResourceTarget.GOLD.integer(25)));

  // BARRACKS — Fall table, unlocks STRENGTH
  public static final Card BARRACKS_CONSTRUCTION =
      new Card(
          "BARRACKS_CONSTRUCTION",
          22.0,
          250.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-40),
              ResourceTarget.FOOD.integer(-20)));

  public static final Card BARRACKS_COMPLETED =
      new Card(
          "BARRACKS",
          15.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.STRENGTH.integer(20),
              ResourceTarget.FOOD.integer(10)));

  // THE GREAT GRANARY — Spring table, win condition (easiest)
  public static final Card THE_GREAT_GRANARY_CONSTRUCTION =
      new Card(
          "THE_GREAT_GRANARY_CONSTRUCTION",
          20.0,
          450.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-35),
              ResourceTarget.FOOD.integer(-25),
              ResourceTarget.GOLD.integer(-10),
              ResourceTarget.STRENGTH.integer(-5)));

  public static final Card THE_GREAT_GRANARY_COMPLETED =
      new Card(
          "THE_GREAT_GRANARY",
          0.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.FOOD.integer(80),
              ResourceTarget.PRODUCTION.integer(20)));

  // THE LIGHTHOUSE — Summer table, win condition (medium)
  public static final Card THE_LIGHTHOUSE_CONSTRUCTION =
      new Card(
          "THE_LIGHTHOUSE_CONSTRUCTION",
          25.0,
          550.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-50),
              ResourceTarget.FOOD.integer(-30),
              ResourceTarget.GOLD.integer(-15),
              ResourceTarget.STRENGTH.integer(-10)));

  public static final Card THE_LIGHTHOUSE_COMPLETED =
      new Card(
          "THE_LIGHTHOUSE",
          0.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(60),
              ResourceTarget.FOOD.integer(40),
              ResourceTarget.GOLD.integer(10)));

  // THE CITADEL — Fall table, win condition (hard)
  public static final Card THE_CITADEL_CONSTRUCTION =
      new Card(
          "THE_CITADEL_CONSTRUCTION",
          30.0,
          700.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-70),
              ResourceTarget.FOOD.integer(-35),
              ResourceTarget.GOLD.integer(-20),
              ResourceTarget.STRENGTH.integer(-15)));

  public static final Card THE_CITADEL_COMPLETED =
      new Card(
          "THE_CITADEL",
          0.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(100),
              ResourceTarget.STRENGTH.integer(30)));

  // THE PALACE — Winter table, win condition (hardest)
  public static final Card THE_PALACE_CONSTRUCTION =
      new Card(
          "THE_PALACE_CONSTRUCTION",
          40.0,
          900.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.PRODUCTION.integer(-100),
              ResourceTarget.FOOD.integer(-60),
              ResourceTarget.GOLD.integer(-30),
              ResourceTarget.STRENGTH.integer(-25)));

  public static final Card THE_PALACE_COMPLETED =
      new Card(
          "THE_PALACE",
          0.0,
          0.0,
          FrequencyType.RECURRING,
          List.of(
              ResourceTarget.FOOD.integer(100),
              ResourceTarget.PRODUCTION.integer(100),
              ResourceTarget.GOLD.integer(30),
              ResourceTarget.STRENGTH.integer(20)));

  // ── Great Work definitions ─────────────────────────────────────────────────

  public static final GreatWork TREASURY =
      new GreatWork(
          "TREASURY",
          TREASURY_CONSTRUCTION,
          TREASURY_COMPLETED,
          4,
          Season.SUMMER,
          false,
          Set.of(ResourceTarget.GOLD));

  public static final GreatWork BARRACKS =
      new GreatWork(
          "BARRACKS",
          BARRACKS_CONSTRUCTION,
          BARRACKS_COMPLETED,
          3,
          Season.FALL,
          false,
          Set.of(ResourceTarget.STRENGTH));

  public static final GreatWork THE_GREAT_GRANARY =
      new GreatWork(
          "THE_GREAT_GRANARY",
          THE_GREAT_GRANARY_CONSTRUCTION,
          THE_GREAT_GRANARY_COMPLETED,
          6,
          Season.SPRING,
          true,
          Set.of());

  public static final GreatWork THE_LIGHTHOUSE =
      new GreatWork(
          "THE_LIGHTHOUSE",
          THE_LIGHTHOUSE_CONSTRUCTION,
          THE_LIGHTHOUSE_COMPLETED,
          8,
          Season.SUMMER,
          true,
          Set.of());

  public static final GreatWork THE_CITADEL =
      new GreatWork(
          "THE_CITADEL",
          THE_CITADEL_CONSTRUCTION,
          THE_CITADEL_COMPLETED,
          10,
          Season.FALL,
          true,
          Set.of());

  public static final GreatWork THE_PALACE =
      new GreatWork(
          "THE_PALACE",
          THE_PALACE_CONSTRUCTION,
          THE_PALACE_COMPLETED,
          12,
          Season.WINTER,
          true,
          Set.of());

  /** Returns the canonical King's Hand Great Work catalog. */
  public static GreatWorkCatalog defaultCatalog() {
    return new GreatWorkCatalog(
        List.of(
            TREASURY,
            BARRACKS,
            THE_GREAT_GRANARY,
            THE_LIGHTHOUSE,
            THE_CITADEL,
            THE_PALACE));
  }
}
