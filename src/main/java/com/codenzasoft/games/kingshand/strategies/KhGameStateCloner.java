package com.codenzasoft.games.kingshand.strategies;

import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.PlayerState;
import com.codenzasoft.games.kingshand.ResourceState;
import com.codenzasoft.games.kingshand.Season;
import com.codenzasoft.games.kingshand.SeatedAdvisor;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces a <b>true deep copy</b> of a {@link GameState} so the search can simulate actions on a
 * throwaway copy without ever mutating the live match.
 *
 * <p><b>Why this is needed.</b> The engine mutates {@link GameState}/{@link PlayerState} in place —
 * there is no copy-on-write and no existing clone. But not everything is mutable: {@code Card},
 * {@code Effect}, {@code ResourceState}, {@code TimeState}, {@code CouncilProposal}, {@code
 * Advisor} and the {@code EffectTarget}s are immutable records/enums, so they are safe to share by
 * reference. The only things that must actually be copied are the per-player mutable collections
 * (board / draw / discard piles, hand, offer lists, accelerated-GW set) and the mutable {@link
 * SeatedAdvisor} (Reforms/Decrees rewrite its card and bonus counters over a run).
 *
 * <p>{@link PlayerState.Builder} already defensively copies every collection handed to it, so
 * cloning a player is mostly a matter of routing its current contents back through the builder; the
 * one place that needs manual care is the council, whose {@link SeatedAdvisor}s are copied field
 * for field.
 */
public final class KhGameStateCloner {

  private KhGameStateCloner() {}

  /**
   * Returns an independent deep copy of {@code src}; mutating the copy never touches {@code src}.
   */
  public static GameState deepCopy(final GameState src) {
    final List<PlayerState> players = new ArrayList<>(src.playerCount());
    for (int seat = 0; seat < src.playerCount(); seat++) {
      players.add(deepCopy(src.getPlayer(seat)));
    }
    // TimeState is an immutable record — safe to share the reference.
    final GameState copy = GameState.multiPlayer(players, src.getTime());
    copy.setResolutionPending(src.isResolutionPending());
    for (final int seat : src.getSubmittedSeats()) {
      copy.markSubmitted(seat);
    }
    return copy;
  }

  /** Returns an independent deep copy of a single kingdom. */
  public static PlayerState deepCopy(final PlayerState p) {
    final ResourceState r = p.getResources();
    final PlayerState.Builder b =
        PlayerState.builder()
            .hand(p.getHand())
            .food(r.food())
            .production(r.production())
            .population(r.population())
            .populationCap(r.populationCap())
            .gold(r.gold())
            .strength(r.strength())
            .unlockedResources(r.unlocked())
            .addChoices(p.getAddChoices())
            .rerollCount(p.getRerollCount())
            .resolutionLog(p.getResolutionLog())
            .council(deepCopyCouncil(p.getCouncil()))
            .councilOfferChoices(p.getCouncilOfferChoices())
            .currentProposal(p.getCurrentProposal())
            .permanentFreeRerolls(p.getPermanentFreeRerolls())
            .acceleratedGreatWorkNames(p.getAcceleratedGreatWorkNames());
    for (final Season s : Season.values()) {
      b.table(s, p.getBoard().get(s));
      b.drawPile(s, p.getDrawPile().get(s));
      b.discardPile(s, p.getDiscardPile().get(s));
    }
    final PlayerState copy = b.build();
    if (p.isForfeited()) {
      copy.forfeit();
    }
    return copy;
  }

  private static List<SeatedAdvisor> deepCopyCouncil(final List<SeatedAdvisor> council) {
    final List<SeatedAdvisor> copy = new ArrayList<>(council.size());
    for (final SeatedAdvisor s : council) {
      copy.add(
          new SeatedAdvisor(
              s.getAdvisorId(),
              s.getCard(), // Card is an immutable record — safe to share.
              s.getReformStacksTaken(),
              s.isDecreeTaken(),
              s.getLastProposalYear(),
              s.getBonusFreeRerolls(),
              s.getBonusExtraAddCandidates(),
              s.getBonusCrossTableBoost(),
              s.getHiredYear()));
    }
    return copy;
  }
}
