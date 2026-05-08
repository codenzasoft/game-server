package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Static catalog of {@link Event}s available to the {@link Phase#RANDOM_EVENT} phase. Holds two
 * pools:
 *
 * <ul>
 *   <li><b>Per-season events</b>
 *   <li><b>Winter-only escalating events</b>
 * </ul>
 *
 * <p>Eligibility is the only filter the catalog applies: an event is eligible iff {@code minYear
 * &le; currentYear}. Random selection / weighting is the resolver's job, not the catalog's.
 *
 * <p>The seasonal map and winter-escalating list are defensively copied at construction so callers
 * can't mutate them after the fact.
 */
public class EventCatalog {

  private final Map<Season, List<Event>> seasonalEvents;
  private final List<Event> winterEscalating;

  public EventCatalog(Map<Season, List<Event>> seasonalEvents, List<Event> winterEscalating) {
    Map<Season, List<Event>> copy = new EnumMap<>(Season.class);
    for (Season s : Season.values()) {
      List<Event> events = seasonalEvents == null ? null : seasonalEvents.get(s);
      copy.put(s, events == null ? List.of() : List.copyOf(events));
    }
    this.seasonalEvents = Collections.unmodifiableMap(copy);
    this.winterEscalating = winterEscalating == null ? List.of() : List.copyOf(winterEscalating);
  }

  /** All per-season events for {@code season}, regardless of year eligibility. */
  public List<Event> getSeasonalEvents(Season season) {
    return seasonalEvents.get(season);
  }

  /** The winter-only escalating event list, regardless of year eligibility. */
  public List<Event> getWinterEscalating() {
    return winterEscalating;
  }

  /**
   * Events eligible in a season given currentYear when the season is winter, the escalating list.
   * Each event is included only if event.minYear() <= currentYear.
   *
   * @return eligible events
   */
  public List<Event> eligibleEvents(Season season, int currentYear) {
    if (season == null) {
      throw new IllegalArgumentException("season cannot be null");
    }

    List<Event> result = new ArrayList<>();
    for (Event e : seasonalEvents.get(season)) {
      if (e.minYear() <= currentYear) {
        result.add(e);
      }
    }
    if (season == Season.WINTER) {
      for (Event e : winterEscalating) {
        if (e.minYear() <= currentYear) {
          result.add(e);
        }
      }
    }
    return List.copyOf(result);
  }

  /** Prototype events, subject to change */
  public static EventCatalog defaultCatalog() {
    Map<Season, List<Event>> seasonal = new EnumMap<>(Season.class);

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
                            new Effect(
                                ResourceTarget.FOOD, 15, ValueType.INTEGER, FrequencyType.SINGLE),
                            new Effect(
                                ResourceTarget.PRODUCTION,
                                -5,
                                ValueType.INTEGER,
                                FrequencyType.SINGLE))),
                    new EventOption(
                        "Conserve seed stock",
                        List.of(
                            new Effect(
                                ResourceTarget.FOOD,
                                5,
                                ValueType.INTEGER,
                                FrequencyType.SINGLE)))))));

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
                            new Effect(
                                ResourceTarget.PRODUCTION,
                                10,
                                ValueType.INTEGER,
                                FrequencyType.SINGLE),
                            new Effect(
                                ResourceTarget.FOOD, -5, ValueType.INTEGER, FrequencyType.SINGLE))),
                    new EventOption("Turn them away", List.of())))));

    seasonal.put(
        Season.FALL,
        List.of(
            new Event(
                "fall.bountiful_harvest",
                Season.FALL,
                1,
                "Fields ripen heavy this year.",
                List.of(
                    new EventOption(
                        "Bring it all in",
                        List.of(
                            new Effect(
                                ResourceTarget.FOOD,
                                20,
                                ValueType.INTEGER,
                                FrequencyType.SINGLE)))))));

    seasonal.put(Season.WINTER, List.of());

    // Winter only escalating events:
    List<Event> winterEscalating =
        List.of(
            new Event(
                "winter.cold_snap",
                Season.WINTER,
                1,
                "A bitter cold snap settles over the land.",
                List.of(
                    new EventOption(
                        "Burn extra firewood",
                        List.of(
                            new Effect(
                                ResourceTarget.PRODUCTION,
                                -5,
                                ValueType.INTEGER,
                                FrequencyType.SINGLE))),
                    new EventOption(
                        "Ration warmth",
                        List.of(
                            new Effect(
                                ResourceTarget.POPULATION,
                                -1,
                                ValueType.INTEGER,
                                FrequencyType.SINGLE))))),
            new Event(
                "winter.long_dark",
                Season.WINTER,
                3,
                "The dark stretches longer this year. Stores run thin.",
                List.of(
                    new EventOption(
                        "Slaughter livestock",
                        List.of(
                            new Effect(
                                ResourceTarget.FOOD, 10, ValueType.INTEGER, FrequencyType.SINGLE),
                            new Effect(
                                ResourceTarget.PRODUCTION,
                                -10,
                                ValueType.INTEGER,
                                FrequencyType.SINGLE))),
                    new EventOption(
                        "Endure",
                        List.of(
                            new Effect(
                                ResourceTarget.FOOD,
                                -10,
                                ValueType.INTEGER,
                                FrequencyType.SINGLE))))),
            new Event(
                "winter.killing_frost",
                Season.WINTER,
                5,
                "A killing frost takes the weakest.",
                List.of(
                    new EventOption(
                        "Mourn",
                        List.of(
                            new Effect(
                                ResourceTarget.POPULATION,
                                -2,
                                ValueType.INTEGER,
                                FrequencyType.SINGLE))))));

    return new EventCatalog(seasonal, winterEscalating);
  }
}
