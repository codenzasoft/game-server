package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.PlayedCard;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class StrategyHelper {

  private StrategyHelper() {}

  public static Stream<Card> filterSuit(final Collection<Card> cards, final Suit suit) {
    return filterSuit(cards, Set.of(suit));
  }

  public static Stream<Card> filterSuit(
      final Collection<Card> cards, final Collection<Suit> suits) {
    return cards.stream().filter(c -> suits.contains(c.getSuit()));
  }

  public static Integer getPoints(final TrickState trick) {
    return getPoints(getPlayedCards(trick));
  }

  public static List<Card> getPlayedCards(final TrickState trick) {
    return trick.cards().stream().map(PlayedCard::card).toList();
  }

  public static Integer getPoints(final Collection<Card> cards) {
    return cards.stream().map(HeartsGame::getPoints).reduce(0, Integer::sum);
  }

  public static Set<Card> getCardsOfSuit(final Collection<Card> cards, final Suit suit) {
    return filterSuit(cards, suit).collect(Collectors.toSet());
  }

  public static List<Card> getHigherCards(
      final Collection<Card> cards, final Suit suit, final Rank rank) {
    return filterSuit(cards, suit).filter(c -> c.outRanks(rank)).toList();
  }

  public static Optional<Card> getMinRank(final Collection<Card> cards, final Suit suit) {
    return cards.stream()
        .filter(c -> c.getSuit().equals(suit))
        .min(Comparator.comparing(Card::getRank));
  }

  public static boolean hasQueenBeenPlayed(final RoundState round) {
    return round.getPlayedCards().contains(HeartsGame.QUEEN_OF_SPADES);
  }

  public static boolean hasQueenBeenPlayed(final TrickState trick) {
    return getPlayedCards(trick).contains(HeartsGame.QUEEN_OF_SPADES);
  }

  public static Optional<Card> getMaxTrump(final TrickState trick) {
    return getMaxRank(getPlayedCards(trick), trick.trump());
  }

  public static Optional<Card> getMaxRank(final Collection<Card> cards, final Suit suit) {
    return cards.stream()
        .filter(c -> c.getSuit().equals(suit))
        .max(Comparator.comparing(Card::getRank));
  }

  public static Optional<Card> getMaxLessThan(final Collection<Card> cards, final Card card) {
    return filterSuit(cards, card.getSuit())
        .filter(c -> c.getRank().compareTo(card.getRank()) < 0)
        .max(Comparator.comparing(Card::getRank));
  }

  public static Set<Suit> getSuits(final Collection<Card> cards) {
    return cards.stream().map(Card::getSuit).collect(Collectors.toSet());
  }
}
