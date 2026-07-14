package com.codenzasoft.games.kingshand.strategies;

import com.codenzasoft.games.kingshand.Card;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.PlayerState;
import java.util.List;
import java.util.Random;

/**
 * Minimal random driver for tests: picks a random offered card during ADD (or skips), immediately
 * ends and runs during PLAY, and declines everything during the council phases. Deterministic when
 * constructed with a seeded {@link Random}.
 */
public class KhRandomStrategy implements KhPlayerStrategy {

  private final Random rng;

  public KhRandomStrategy() {
    this(new Random());
  }

  public KhRandomStrategy(final Random rng) {
    this.rng = rng;
  }

  @Override
  public void act(final GameEngine engine, final int seat) {
    final PlayerState me = engine.getState().getPlayer(seat);
    switch (engine.getState().getTime().phase()) {
      case ADD -> {
        final List<Card> choices = me.getAddChoices();
        if (choices.isEmpty()) {
          engine.skipAdd();
        } else {
          engine.pickCard(choices.get(rng.nextInt(choices.size())));
        }
      }
      case PLAY -> engine.endAndRun();
      case COUNCIL_ADD -> engine.skipCouncilAdd();
      case COUNCIL_MEETING -> engine.passCouncilProposal();
    }
  }
}
