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
                        "Accept the flooding", List.of(ResourceTarget.FOOD.integer(-15))))),
            new Event(
                "spring.blight",
                Season.SPRING,
                2,
                "A fungal blight spreads across the young crops.",
                List.of(
                    new EventOption(
                        "Treat the fields aggressively",
                        List.of(ResourceTarget.PRODUCTION.integer(-12))),
                    new EventOption(
                        "Accept the losses", List.of(ResourceTarget.FOOD.integer(-15))))),
            new Event(
                "spring.returning_settlers",
                Season.SPRING,
                1,
                "Families who fled last winter trickle back, lean but willing to work.",
                List.of(
                    new EventOption(
                        "Take them in",
                        List.of(
                            ResourceTarget.POPULATION.integer(3),
                            ResourceTarget.FOOD.integer(-12))),
                    new EventOption("Turn them away", List.of()))),
            new Event(
                "spring.river_merchants",
                Season.SPRING,
                1,
                "Merchants on river barges offer goods in exchange for provisions.",
                List.of(
                    new EventOption(
                        "Trade food for goods",
                        List.of(
                            ResourceTarget.FOOD.integer(-15),
                            ResourceTarget.PRODUCTION.integer(20))),
                    new EventOption(
                        "Trade goods for food",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-15),
                            ResourceTarget.FOOD.integer(20))),
                    new EventOption("Decline", List.of()))),
            new Event(
                "spring.soil_survey",
                Season.SPRING,
                3,
                "A surveyor discovers rich alluvial soil along the river delta.",
                List.of(
                    new EventOption(
                        "Divert workers to till it",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-10),
                            ResourceTarget.FOOD.integer(25))),
                    new EventOption("Stay the course", List.of(ResourceTarget.FOOD.integer(5))))),
            new Event(
                "spring.wolf_pack",
                Season.SPRING,
                2,
                "A wolf pack has been raiding livestock pens each night.",
                List.of(
                    new EventOption(
                        "Hunt them down",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-8), ResourceTarget.FOOD.integer(5))),
                    new EventOption(
                        "Reinforce the pens", List.of(ResourceTarget.PRODUCTION.integer(-5))),
                    new EventOption("Do nothing", List.of(ResourceTarget.FOOD.integer(-10)))))));

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
                            ResourceTarget.PRODUCTION.integer(-10),
                            ResourceTarget.POPULATION.integer(2))),
                    new EventOption(
                        "Decline — there is work to do",
                        List.of(ResourceTarget.POPULATION.integer(-5))))),
            new Event(
                "summer.wildfire",
                Season.SUMMER,
                2,
                "Wildfire races through dry grasslands toward the settlement's fields.",
                List.of(
                    new EventOption(
                        "Fight the flames",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-15),
                            ResourceTarget.FOOD.integer(5))),
                    new EventOption(
                        "Retreat and regroup", List.of(ResourceTarget.FOOD.integer(-20))))),
            new Event(
                "summer.plague",
                Season.SUMMER,
                3,
                "A mysterious fever spreads quickly through the settlement.",
                List.of(
                    new EventOption(
                        "Quarantine and treat",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-15),
                            ResourceTarget.POPULATION.integer(-1))),
                    new EventOption(
                        "Pray for recovery", List.of(ResourceTarget.POPULATION.integer(-3))))),
            new Event(
                "summer.wandering_tribe",
                Season.SUMMER,
                1,
                "A nomadic tribe camps at the border, seeking trade.",
                List.of(
                    new EventOption(
                        "Open trade",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(10),
                            ResourceTarget.FOOD.integer(-5))),
                    new EventOption(
                        "Invite them to stay",
                        List.of(
                            ResourceTarget.POPULATION.integer(2),
                            ResourceTarget.FOOD.integer(-10))),
                    new EventOption("Send them away", List.of()))),
            new Event(
                "summer.bumper_crop",
                Season.SUMMER,
                1,
                "Ideal conditions lead to an unexpectedly rich early harvest.",
                List.of(
                    new EventOption(
                        "Harvest everything now",
                        List.of(
                            ResourceTarget.FOOD.integer(20),
                            ResourceTarget.PRODUCTION.integer(-5))),
                    new EventOption(
                        "Harvest carefully", List.of(ResourceTarget.FOOD.integer(15))))),
            new Event(
                "summer.brigands",
                Season.SUMMER,
                2,
                "A band of brigands harasses supply routes and raids outlying farms.",
                List.of(
                    new EventOption(
                        "Send a militia",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-10),
                            ResourceTarget.FOOD.integer(5))),
                    new EventOption(
                        "Pay them off", List.of(ResourceTarget.PRODUCTION.integer(-15))),
                    new EventOption("Ignore them", List.of(ResourceTarget.FOOD.integer(-20)))))));

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
                    new EventOption("Wave them past", List.of()))),
            new Event(
                "fall.failed_harvest",
                Season.FALL,
                2,
                "Unseasonable weather ruins a third of this year's crop.",
                List.of(
                    new EventOption(
                        "Rush alternative planting",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-15),
                            ResourceTarget.FOOD.integer(-5))),
                    new EventOption(
                        "Accept the shortfall", List.of(ResourceTarget.FOOD.integer(-25))))),
            new Event(
                "fall.artisan_visit",
                Season.FALL,
                2,
                "Skilled artisans offer their services before winter closes the roads.",
                List.of(
                    new EventOption(
                        "Hire them",
                        List.of(
                            ResourceTarget.FOOD.integer(-10),
                            ResourceTarget.PRODUCTION.integer(25))),
                    new EventOption("Let them pass", List.of()))),
            new Event(
                "fall.bandit_raid",
                Season.FALL,
                3,
                "Bandits strike outlying storehouses under cover of night.",
                List.of(
                    new EventOption(
                        "Organise a defence",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-10),
                            ResourceTarget.FOOD.integer(-5))),
                    new EventOption(
                        "Surrender the stores", List.of(ResourceTarget.FOOD.integer(-25))))),
            new Event(
                "fall.new_arrivals",
                Season.FALL,
                1,
                "A large group of settlers arrives, hoping to winter with the settlement.",
                List.of(
                    new EventOption(
                        "Settle them",
                        List.of(
                            ResourceTarget.POPULATION.integer(4),
                            ResourceTarget.FOOD.integer(-15))),
                    new EventOption("Turn them away", List.of()))),
            new Event(
                "fall.wild_game",
                Season.FALL,
                1,
                "Hunters report abundant game in the autumn forests.",
                List.of(
                    new EventOption(
                        "Send hunting parties",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-5),
                            ResourceTarget.FOOD.integer(20))),
                    new EventOption("Stay focused on harvest work", List.of())))));

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
                            ResourceTarget.POPULATION.integer(-1))))),
            new Event(
                "winter.blizzard",
                Season.WINTER,
                1,
                "A fierce blizzard traps workers indoors for days on end.",
                List.of(
                    new EventOption(
                        "Clear the roads and keep working",
                        List.of(ResourceTarget.PRODUCTION.integer(-8))),
                    new EventOption("Stay sheltered", List.of(ResourceTarget.FOOD.integer(-10))))),
            new Event(
                "winter.sickness",
                Season.WINTER,
                2,
                "A coughing sickness spreads through the settlement in the cold.",
                List.of(
                    new EventOption(
                        "Quarantine and treat",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-10),
                            ResourceTarget.POPULATION.integer(-1))),
                    new EventOption(
                        "Hope it passes", List.of(ResourceTarget.POPULATION.integer(-2))))),
            new Event(
                "winter.frozen_stores",
                Season.WINTER,
                3,
                "Bitter cold seeps into the storerooms and ruins some supplies.",
                List.of(
                    new EventOption(
                        "Salvage what you can",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-8),
                            ResourceTarget.FOOD.integer(-5))),
                    new EventOption(
                        "Accept the losses", List.of(ResourceTarget.FOOD.integer(-20))))),
            new Event(
                "winter.wolves",
                Season.WINTER,
                2,
                "A bold wolf pack circles the settlement, attacking livestock each night.",
                List.of(
                    new EventOption(
                        "Drive them off", List.of(ResourceTarget.PRODUCTION.integer(-8))),
                    new EventOption(
                        "Let them take what they will",
                        List.of(ResourceTarget.FOOD.integer(-12))))),
            new Event(
                "winter.deserters",
                Season.WINTER,
                4,
                "Desperate settlers pack their belongings and slip away under cover of darkness.",
                List.of(
                    new EventOption(
                        "Pay them to stay",
                        List.of(
                            ResourceTarget.PRODUCTION.integer(-8),
                            ResourceTarget.POPULATION.integer(-1))),
                    new EventOption(
                        "Let them go", List.of(ResourceTarget.POPULATION.integer(-3)))))));

    return new EventCatalog(seasonal);
  }
}
