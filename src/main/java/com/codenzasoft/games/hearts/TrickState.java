package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Suit;
import org.springframework.data.util.Pair;

import java.util.*;

public record TrickState(int number, Player leader, List<Pair<Player, Card>> cards) {

  private static class PairComparator implements Comparator<Pair<Player, Card>> {
    @Override
    public int compare(final Pair<Player, Card> p1, final Pair<Player, Card> p2) {
      return p1.getSecond().getRank().compareTo(p2.getSecond().getRank());
    }
  }

  private static final PairComparator PAIR_COMPARATOR = new PairComparator();

  static TrickState with(int number, Player leader) {
    return new TrickState(number, leader, List.of());
  }

  TrickState playCard(final Player player, final Card card) {
    final List<Pair<Player, Card>> nextCards = new ArrayList<>(cards);
    nextCards.add(Pair.of(player, card));
    return new TrickState(number, leader, Collections.unmodifiableList(nextCards));
  }

  boolean isComplete() {
    return cards.size() == 4;
  }

  Optional<Suit> getTrump() {
    if (cards.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(cards.get(0).getSecond().getSuit());
  }

  Optional<Pair<Player, Card>> getWinner() {
    return getTrump()
        .flatMap(
            suit ->
                cards.stream()
                    .filter(pair -> pair.getSecond().getSuit().equals(suit))
                    .max(PAIR_COMPARATOR));
  }

  Optional<Player> getNextPlayer(final GameState gameState) {
    if (isComplete()) {
      return Optional.empty();
    }
    if (cards.isEmpty()) {
      return Optional.of(leader());
    }
    final Pair<Player, Card> prevCard = cards.get(cards.size() - 1);
    final Player nextPlayer = HeartsGame.getPlayerLeftOf(prevCard.getFirst(), gameState.players());
    return Optional.of(nextPlayer);
  }

  int getPoints() {
    return cards.stream().map(Pair::getSecond).mapToInt(HeartsGame::getPoints).sum();
  }
}
