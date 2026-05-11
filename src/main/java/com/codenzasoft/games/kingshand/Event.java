package com.codenzasoft.games.kingshand;

import java.util.List;

/**
 * A random event that can fire during the {@link Phase#RANDOM_EVENT} phase. Carries a stable
 * identifier, the {@link Season} it belongs to, the earliest game year it becomes eligible ({@code
 * minYear}), a human-readable description, and the list of {@link EventOption}s the player can
 * choose between.
 *
 * <p>Eligibility is a static property of the event ({@code minYear &le; currentYear}); selection /
 * scaling logic lives in {@link EventCatalog}.
 *
 * <p>The options list is defensively copied at construction so callers can't mutate it after the
 * fact.
 */
public record Event(
    String id, Season season, int minYear, String description, List<EventOption> options) {

  public Event {
    options = options == null ? List.of() : List.copyOf(options);
  }
}
