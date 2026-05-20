package com.codenzasoft.games.kingshand;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Static catalog of {@link Event}s available to the {@link Phase#RANDOM_EVENT} phase.
 *
 * <ul>
 *   <li><b>Per-season events</b>
 * </ul>
 *
 * <p>Eligibility is the only filter the catalog applies: an event is eligible iff {@code minYear
 * &le; currentYear}. Random selection / weighting is the resolver's job, not the catalog's.
 */
public class EventCatalog {

  private final Map<Season, List<Event>> seasonalEvents;

  public EventCatalog(Map<Season, List<Event>> seasonalEvents) {
    Map<Season, List<Event>> copy = new EnumMap<>(Season.class);
    for (Season s : Season.values()) {
      List<Event> events = seasonalEvents == null ? null : seasonalEvents.get(s);
      copy.put(s, events == null ? List.of() : List.copyOf(events));
    }
    this.seasonalEvents = Collections.unmodifiableMap(copy);
  }

  /** All per-season events for {@code season}, regardless of year eligibility. */
  public List<Event> getSeasonalEvents(Season season) {
    return seasonalEvents.get(season);
  }

  /**
   * Events eligible in {@code season} given {@code currentYear}: every event in the season's pool
   * whose {@link Event#minYear()} is at most {@code currentYear}.
   *
   * @return an immutable snapshot of the eligible events
   */
  public List<Event> eligibleEvents(Season season, int currentYear) {
    if (season == null) {
      throw new IllegalArgumentException("season cannot be null");
    }
    return seasonalEvents.get(season).stream().filter(e -> e.minYear() <= currentYear).toList();
  }

  /** Prototype events, subject to change */
  public static EventCatalog defaultCatalog() {
    Map<Season, List<Event>> seasonal = new EnumMap<>(Season.class);

    // ── Spring ────────────────────────────────────────────────────────────────
    seasonal.put(
        Season.SPRING,
        List.of(
            new Event(
                "spring.early_thaw",
                Season.SPRING,
                1,
                "An early thaw promises a long growing season — but at what cost?",
                List.of(
                    new EventOption(
                        "Plant aggressively",
                        List.of(
                            ResourceTarget.FOOD.integer(15),
                            ResourceTarget.PRODUCTION.integer(-5))),
                    new EventOption(
                        "Conserve seed stock", List.of(ResourceTarget.FOOD.integer(5))))),
            new Event(
                "spring.migrants",
                Season.SPRING,
                1,
                "Wandering families arrive at the border, seeking land and a fresh start.",
                List.of(
                    new EventOption(
                        "Welcome them in",
                        List.of(
                            ResourceTarget.POPULATION.integer(2),
                            ResourceTarget.FOOD.integer(-10))),
                    new EventOption("Redirect them onward", List.of()))),
            new Event(
                "spring.floods",
                Season.SPRING,
                2,
                "Snowmelt swells the rivers. Low-lying fields sit under water.",
                List.of(
                    new EventOption(
                        "Shore up the banks", List.of(ResourceTarget.PRODUCTION.integer(-10))),
                    new EventOption(
                        "Accept the flooding", List.of(ResourceTarget.FOOD.integer(-15)))))));

    // ── Summer ────────────────────────────────────────────────────────────────
    seasonal.put(
        Season.SUMMER,
        List.of(
            new Event(
                "summer.traveling_merchants",
                Season.SUMMER,
                1,
                "A caravan of merchants asks for shelter in exchange for trade.",
                List.of(
                    new EventOption(
                        "Welcome them",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(10),
                            ResourceTarget.FOOD.integer(-5))),
                    new EventOption("Turn them away", List.of()))),
            new Event(
                "summer.drought",
                Season.SUMMER,
                1,
                "A long dry spell parches the fields. The harvest is at risk.",
                List.of(
                    new EventOption(
                        "Dig emergency wells",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-15),
                            ResourceTarget.FOOD.integer(5))),
                    new EventOption("Pray for rain", List.of(ResourceTarget.FOOD.integer(-20))))),
            new Event(
                "summer.festival",
                Season.SUMMER,
                2,
                "The people clamour for a midsummer festival. Spirits are low.",
                List.of(
                    new EventOption(
                        "Hold the feast",
                        List.of(
                            ResourceTarget.FOOD.integer(-10),
                            ResourceTarget.POPULATION.integer(1))),
                    new EventOption("Decline — there is work to do", List.of())))));

    // ── Fall ──────────────────────────────────────────────────────────────────
    seasonal.put(
        Season.FALL,
        List.of(
            new Event(
                "fall.bountiful_harvest",
                Season.FALL,
                1,
                "Fields ripen heavy this year.",
                List.of(
                    new EventOption("Bring it all in", List.of(ResourceTarget.FOOD.integer(20))))),
            new Event(
                "fall.early_frost",
                Season.FALL,
                1,
                "A bitter frost arrives weeks ahead of schedule. Unharvested crops are at risk.",
                List.of(
                    new EventOption(
                        "Rush the harvest",
                        List.of(
                            ResourceTarget.FOOD.integer(10),
                            ResourceTarget.PRODUCTION.integer(-10))),
                    new EventOption("Accept the loss", List.of(ResourceTarget.FOOD.integer(-15))))),
            new Event(
                "fall.trade_caravan",
                Season.FALL,
                2,
                "A trade caravan passes through on its way south before winter closes the roads.",
                List.of(
                    new EventOption(
                        "Stock up on provisions",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-15),
                            ResourceTarget.FOOD.integer(25))),
                    new EventOption("Wave them past", List.of())))));

    // ── Winter ────────────────────────────────────────────────────────────────
    seasonal.put(
        Season.WINTER,
        List.of(
            new Event(
                "winter.cold_snap",
                Season.WINTER,
                1,
                "A bitter cold snap settles over the land.",
                List.of(
                    new EventOption(
                        "Burn extra firewood", List.of(ResourceTarget.PRODUCTION.integer(-5))),
                    new EventOption(
                        "Ration warmth", List.of(ResourceTarget.POPULATION.integer(-1))))),
            new Event(
                "winter.long_dark",
                Season.WINTER,
                3,
                "The dark stretches longer this year. Stores run thin.",
                List.of(
                    new EventOption(
                        "Slaughter livestock",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-10),
                            ResourceTarget.FOOD.integer(-5))),
                    new EventOption(
                        "Endure and ration", List.of(ResourceTarget.FOOD.integer(-15))))),
            new Event(
                "winter.killing_frost",
                Season.WINTER,
                5,
                "A killing frost takes the weakest.",
                List.of(
                    new EventOption(
                        "Mourn the dead", List.of(ResourceTarget.POPULATION.integer(-2))),
                    new EventOption(
                        "Sacrifice the surplus to save lives",
                        List.of(
                            ResourceTarget.FOOD.integer(-20),
                            ResourceTarget.POPULATION.integer(-1)))))));

    return new EventCatalog(seasonal);
  }
}
