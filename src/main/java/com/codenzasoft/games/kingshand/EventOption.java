package com.codenzasoft.games.kingshand;

import java.util.List;

/**
 * One choice the player can pick when an {@link Event} fires. Carries a human-readable description
 * and an arbitrary list of {@link Effect}s — the same effect data shape used by {@link Card}, so
 * the same resolver can apply them.
 *
 * <p>The effects list is defensively copied at construction so callers can't mutate it after the
 * fact.
 */
public record EventOption(String description, List<Effect> effects) {

  public EventOption {
    effects = effects == null ? List.of() : List.copyOf(effects);
  }
}
