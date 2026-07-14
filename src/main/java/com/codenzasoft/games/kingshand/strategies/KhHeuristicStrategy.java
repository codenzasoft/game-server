package com.codenzasoft.games.kingshand.strategies;

import com.codenzasoft.games.kingshand.Advisor;
import com.codenzasoft.games.kingshand.Card;
import com.codenzasoft.games.kingshand.CouncilProposal;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.PlayerState;
import com.codenzasoft.games.kingshand.ProposalKind;
import java.util.Comparator;
import java.util.Optional;

/**
 * Simple greedy heuristic driver for a bot seat. Deliberately unsophisticated — strength can come
 * later; the goal is a legal, always-terminating opponent so a solo human can play a full match:
 *
 * <ul>
 *   <li><b>ADD</b> — picks the cheapest offered card (never rerolls); skips if nothing is offered.
 *   <li><b>PLAY</b> — greedily places every affordable hand card (upgrades or open slots), then
 *       ends and runs.
 *   <li><b>COUNCIL_ADD</b> — hires the cheapest affordable advisor while the council has room,
 *       otherwise skips.
 *   <li><b>COUNCIL_MEETING</b> — accepts Decrees (one-time, upside-only civic projects), passes on
 *       Reforms (which drain the advisor's own card).
 * </ul>
 */
public class KhHeuristicStrategy implements KhPlayerStrategy {

  @Override
  public void act(final GameEngine engine, final int seat) {
    final GameState state = engine.getState();
    final PlayerState me = state.getPlayer(seat);
    switch (state.getTime().phase()) {
      case ADD -> actAdd(engine, me);
      case PLAY -> actPlay(engine, me, state);
      case COUNCIL_ADD -> actCouncilAdd(engine, me);
      case COUNCIL_MEETING -> actCouncilMeeting(engine, me);
    }
  }

  private void actAdd(final GameEngine engine, final PlayerState me) {
    final Optional<Card> cheapest =
        me.getAddChoices().stream().min(Comparator.comparingDouble(Card::productionRequirement));
    if (cheapest.isPresent()) {
      engine.pickCard(cheapest.get());
    } else {
      engine.skipAdd();
    }
  }

  private void actPlay(final GameEngine engine, final PlayerState me, final GameState state) {
    // Greedily place affordable hand cards, cheapest first. Bounded by the starting hand size, so
    // this always terminates even if a placement is rejected.
    final int handSize = me.getHand().size();
    for (int i = 0; i < handSize; i++) {
      final Optional<Card> playable =
          me.getHand().stream()
              .filter(c -> me.getResources().production() >= c.productionRequirement())
              .filter(
                  c ->
                      me.tableContains(state.getTime().season(), c)
                          || me.getTable(state.getTime().season()).size()
                              < GameEngine.MAX_TABLE_SIZE)
              .min(Comparator.comparingDouble(Card::productionRequirement));
      if (playable.isEmpty()) {
        break;
      }
      try {
        engine.placeCard(playable.get());
      } catch (final IllegalStateException e) {
        break; // validation disagreed with our pre-check; stop placing rather than loop
      }
    }
    engine.endAndRun();
  }

  private void actCouncilAdd(final GameEngine engine, final PlayerState me) {
    if (me.getCouncil().size() < GameEngine.MAX_COUNCIL_SIZE) {
      final Optional<Advisor> affordable =
          me.getCouncilOfferChoices().stream()
              .filter(a -> me.getResources().gold() >= a.hiringCost())
              .min(Comparator.comparingInt(Advisor::hiringCost));
      if (affordable.isPresent()) {
        try {
          engine.hireAdvisor(affordable.get());
          return;
        } catch (final IllegalStateException e) {
          // fall through to skip
        }
      }
    }
    engine.skipCouncilAdd();
  }

  private void actCouncilMeeting(final GameEngine engine, final PlayerState me) {
    final CouncilProposal proposal = me.getCurrentProposal();
    if (proposal != null && proposal.kind() == ProposalKind.DECREE) {
      try {
        engine.acceptCouncilProposal();
        return;
      } catch (final IllegalStateException e) {
        // fall through to pass
      }
    }
    engine.passCouncilProposal();
  }
}
