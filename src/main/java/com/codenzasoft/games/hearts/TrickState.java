package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Suit;
import java.util.*;
import org.springframework.data.util.Pair;

public record TrickState(int number, String leader, List<Pair<String, Card>> cards) {

  private static class PairComparator implements Comparator<Pair<String, Card>> {
    @Override
    public int compare(final Pair<String, Card> p1, final Pair<String, Card> p2) {
      return p1.getSecond().getRank().compareTo(p2.getSecond().getRank());
    }
  }

  private static final PairComparator PAIR_COMPARATOR = new PairComparator();

  static TrickState with(int number, String leader) {
    return new TrickState(number, leader, List.of());
  }

  TrickState playCard(final String player, final Card card) {
    final List<Pair<String, Card>> nextCards = new ArrayList<>(cards);
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

  Optional<Pair<String, Card>> getWinner() {
    return getTrump()
        .flatMap(
            suit ->
                cards.stream()
                    .filter(pair -> pair.getSecond().getSuit().equals(suit))
                    .max(PAIR_COMPARATOR));
  }

  Optional<String> getNextPlayer(final HeartsState gameState) {
    if (isComplete()) {
      return Optional.empty();
    }
    if (cards.isEmpty()) {
      return Optional.of(leader());
    }
    final Pair<String, Card> prevCard = cards.get(cards.size() - 1);
    final String nextPlayer = gameState.getPlayerLeftOf(prevCard.getFirst());
    return Optional.of(nextPlayer);
  }

  int getPoints() {
    return cards.stream().map(Pair::getSecond).mapToInt(HeartsGame::getPoints).sum();
  }
}
