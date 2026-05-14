package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import org.springframework.data.util.Pair;

import java.util.*;

/**
 * A {@link RoundState} describes the point-in-time state of a round.
 *
 * <p>The order of operations for a {@link RoundState} is as follows.
 *
 * <ol>
 *   <li>Create an initial {@link RoundState} with its number, pass direction and dealt cards via
 *       {@link #with(int, Pass, Player, Map)}.
 *   <li>Set selected pass cards for each player via {@link #setPass(Player, List)}, unless this is
 *       a {@link Pass#KEEPER} round.
 *   <li>Execute the pass via {@link #passCards(List)}.
 *   <li>Play tricks until the round is complete. For each trick, this entails:
 *       <oL>
 *         <li>Starting a new trick.
 *         <li>Playing a card for each player.
 *       </oL>
 * </ol>
 *
 * @param number The round number, beginning at one.
 * @param pass The {@link Pass} direction.
 * @param dealer The designated dealer for the round.
 * @param deal The {@link Card}s dealt to each {@link Player} at the beginning of the round.
 * @param passes The pass {@link Card}s selected for each {@link Player}.
 * @param passComplete Whether the card pass has been executed.
 * @param tricks The sequence of tricks played in this round.
 * @param hands The {@link Card}s remaining in each {@link Player}s hand.
 */
public record RoundState(
    int number,
    Pass pass,
    Player dealer,
    Map<Player, List<Card>> deal,
    Map<Player, List<Card>> passes,
    boolean passComplete,
    List<TrickState> tricks,
    Map<Player, List<Card>> hands) {

  public static RoundState with(
      final int number, final Pass pass, final Player dealer, final Map<Player, List<Card>> deal) {
    final Map<Player, List<Card>> hands = new HashMap<>();
    deal.forEach((player, cards) -> hands.put(player, Collections.unmodifiableList(cards)));
    final Map<Player, List<Card>> cards = Collections.unmodifiableMap(hands);
    return new RoundState(number, pass, dealer, cards, Map.of(), false, List.of(), cards);
  }

  RoundState setPass(final Player player, final List<Card> cards) {
    if (pass.equals(Pass.KEEPER)) {
      throw new IllegalStateException("cannot pass in keeper round");
    }
    if (isPassComplete()) {
      throw new IllegalStateException("pass has already been executed");
    }
    if (cards.size() != 3) {
      throw new IllegalStateException("a pass must have 3 cards");
    }
    if (passes.containsKey(player)) {
      throw new IllegalStateException("player has already selected pass");
    }
    if (!deal.get(player).containsAll(cards)) {
      throw new IllegalStateException("player does not have those cards");
    }
    final Map<Player, List<Card>> nextPasses = new HashMap<>(passes);
    nextPasses.put(player, Collections.unmodifiableList(cards));
    return new RoundState(
        number, pass, dealer, deal, Collections.unmodifiableMap(nextPasses), false, tricks, hands);
  }

  public boolean isPassReady() {
    return passes.size() == 4;
  }

  public boolean isPassComplete() {
    return passComplete;
  }

  RoundState passCards(final List<Player> players) {
    if (!pass.equals(Pass.KEEPER)) {
      if (!isPassReady()) {
        throw new IllegalStateException("all passes must be selected to execute the pass");
      }
      final Map<Player, List<Card>> nextHands = new HashMap<>();
      hands.forEach((player, hand) -> nextHands.put(player, new ArrayList<>(hand)));
      for (final Player p : players) {
        final List<Card> thePass = passes.get(p);
        final Player recipient = HeartsGame.getPlayerRelativeTo(p, pass, players);
        final List<Card> playerHand = nextHands.get(p);
        final List<Card> recipientHand = nextHands.get(recipient);
        playerHand.removeAll(thePass);
        recipientHand.addAll(thePass);
      }
      return new RoundState(number, pass, dealer, deal, passes, true, tricks, nextHands);
    }
    return new RoundState(number, pass, dealer, deal, passes, true, tricks, hands);
  }

  RoundState startNewTrick() {
    if (isComplete()) {
      throw new IllegalStateException("cannot begin a new trick - round is complete");
    }
    final Optional<Player> leader;
    if (tricks.isEmpty()) {
      leader = findLeader(hands);
    } else {
      final TrickState previousTrick = tricks.get(tricks.size() - 1);
      if (!previousTrick.isComplete()) {
        throw new IllegalStateException("current trick is not complete");
      }
      leader = previousTrick.getWinner().map(Pair::getFirst);
    }
    if (leader.isEmpty()) {
      throw new IllegalStateException("no leader found");
    }
    final List<TrickState> nextTricks = new ArrayList<>(tricks);
    nextTricks.add(TrickState.with(tricks().size() + 1, leader.get()));
    return new RoundState(number, pass, dealer, deal, passes, passComplete, nextTricks, hands);
  }

  private Optional<Player> findLeader(final Map<Player, List<Card>> playerHands) {
    return playerHands.entrySet().stream()
        .filter(entry -> entry.getValue().contains(HeartsGame.TWO_OF_CLUBS))
        .findFirst()
        .map(Map.Entry::getKey);
  }

  RoundState playCard(final GameState game, final Player player, final Card card) {
    final List<TrickState> nextTricks;
    if (tricks.isEmpty()) {
      throw new IllegalStateException("cannot play a card without tricks");
    }
    final TrickState currTrick = tricks.get(tricks.size() - 1);
    if (currTrick.isComplete()) {
      throw new IllegalStateException("cannot play a card in a completed trick");
    }
    final Optional<Player> expectedPlayer = getNextPlayer(game);
    if (expectedPlayer.isEmpty()) {
      throw new IllegalStateException("no next player found");
    }
    if (!player.equals(expectedPlayer.get())) {
      throw new IllegalStateException("player is not the next player");
    }

    final Map<Player, List<Card>> nextHands = new HashMap<>();
    for (final Player p : hands.keySet()) {
      if (p.equals(player)) {
        final List<Card> nextCards = new ArrayList<>(hands.get(p));
        if (!nextCards.remove(card)) {
          throw new IllegalStateException("player does not have played card");
        }
        nextHands.put(p, Collections.unmodifiableList(nextCards));
      } else {
        nextHands.put(p, hands.get(p));
      }
    }
    if (tricks.size() == 1) {
      nextTricks = new ArrayList<>();
    } else {
      nextTricks = new ArrayList<>(tricks);
      nextTricks.remove(tricks.size() - 1);
    }

    final TrickState nextTrick = currTrick.playCard(player, card);
    nextTricks.add(nextTrick);

    return new RoundState(
        number,
        pass,
        dealer,
        deal,
        passes,
        passComplete,
        Collections.unmodifiableList(nextTricks),
        nextHands);
  }

  boolean isComplete() {
    return countCards(hands()) == 0;
  }

  private int countCards(final Map<Player, List<Card>> hands) {
    return hands.values().stream().mapToInt(List::size).sum();
  }

  public List<Card> getHand(final Player player) {
    return hands.get(player);
  }

  Optional<Player> getNextPlayer(final GameState gameState) {
    return getCurrentTrick().flatMap(t -> t.getNextPlayer(gameState));
  }

  Optional<TrickState> getCurrentTrick() {
    if (tricks.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(tricks.get(tricks.size() - 1));
  }
}
