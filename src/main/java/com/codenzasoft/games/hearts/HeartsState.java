package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The state of a hearts of hearts.
 *
 * @param players The players in clockwise order, identified by username.
 * @param rounds The state of played rounds.
 * @param isOver Whether the hearts game is over or has been quit.
 */
public record HeartsState(List<String> players, List<RoundState> rounds, boolean isOver) {

  private static class IseSupplier implements Supplier<IllegalStateException> {

    private final String message;

    public IseSupplier(String message) {
      this.message = message;
    }

    @Override
    public IllegalStateException get() {
      return new IllegalStateException(message);
    }
  }

  public static HeartsState with(final List<String> players) {
    return new HeartsState(players, List.of(), false);
  }

  public String getPlayerRelativeTo(final String player, final Pass pass) {
    int index = Math.floorMod(players.indexOf(player) + pass.getOffset(), players.size());
    return players.get(index);
  }

  public String getPlayerLeftOf(final String player) {
    return getPlayerRelativeTo(player, Pass.LEFT);
  }

  public HeartsState setOver() {
    return new HeartsState(players(), rounds(), true);
  }

  HeartsState startNewRound(final String dealer, final Map<String, List<Card>> deal) {
    if (isOver()) {
      throw new IllegalStateException("hearts over - unable to start a new round");
    }
    final Optional<RoundState> currentRound = getCurrentRound();
    final Pass nextPass;
    if (currentRound.isEmpty()) {
      nextPass = Pass.LEFT;
    } else {
      final RoundState prevRound = currentRound.get();
      if (!prevRound.isComplete()) {
        throw new IllegalStateException(
            "current round is not complete - unable to start a new round");
      }
      nextPass = prevRound.pass().nextPass();
    }
    final Map<String, List<Card>> immutableDeal =
        deal.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    final RoundState nextRound =
        RoundState.with(rounds().size() + 1, nextPass, dealer, immutableDeal);
    final List<RoundState> nextRounds = new ArrayList<>(rounds);
    nextRounds.add(nextRound);
    return new HeartsState(players(), nextRounds, isOver());
  }

  public Optional<RoundState> getCurrentRound() {
    if (rounds.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rounds.get(rounds.size() - 1));
  }

  public RoundState getRound() {
    return getCurrentRound().orElseThrow();
  }

  public Optional<TrickState> getCurrentTrick() {
    return getCurrentRound().flatMap(RoundState::getCurrentTrick);
  }

  HeartsState setPass(final String player, final List<Card> cards) {
    return replaceLastRound(
        getCurrentRound()
            .map(r -> r.setPass(player, cards))
            .orElseThrow(new IseSupplier("unable to set pass - no current round")));
  }

  HeartsState passCards() {
    return replaceLastRound(
        getCurrentRound()
            .map(r -> r.passCards(this))
            .orElseThrow(new IseSupplier("unable to pass cards - no current round")));
  }

  HeartsState startNewTrick() {
    return replaceLastRound(
        getCurrentRound()
            .map(RoundState::startNewTrick)
            .orElseThrow(new IseSupplier("unable to start trick - no current round")));
  }

  HeartsState playCard(final String player, final Card card) {
    return replaceLastRound(
        getCurrentRound()
            .map(r -> r.playCard(this, player, card))
            .orElseThrow(new IseSupplier("unable to play card - no current round")));
  }

  public List<Card> getHand(final Player player) {
    return getCurrentRound().map(r -> r.getHand(player)).orElse(Collections.emptyList());
  }

  private IllegalStateException message(final String message) {
    return new IllegalStateException(message);
  }

  private HeartsState replaceLastRound(final RoundState replacement) {
    final List<RoundState> nextRounds = new ArrayList<>(rounds);
    nextRounds.remove(nextRounds.size() - 1);
    nextRounds.add(replacement);
    return new HeartsState(players(), nextRounds, isOver());
  }
}
