package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Suit;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public record TrickState(
    @JsonProperty("number") int number,
    @JsonProperty("leader") String leader,
    @JsonProperty("playedCards") List<PlayedCard> cards) {

  private static final Comparator<PlayedCard> BY_RANK =
      Comparator.comparing(pc -> pc.card().getRank());

  static TrickState with(int number, String leader) {
    return new TrickState(number, leader, List.of());
  }

  TrickState playCard(final String player, final Card card) {
    final List<PlayedCard> nextCards = new ArrayList<>(cards);
    nextCards.add(new PlayedCard(player, card));
    return new TrickState(number, leader, Collections.unmodifiableList(nextCards));
  }

  boolean isComplete() {
    return cards.size() == 4;
  }

  @JsonProperty("trump")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Suit trump() {
    return getTrump().orElse(null);
  }

  @JsonIgnore
  public Optional<Suit> getTrump() {
    if (cards.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(cards.get(0).card().getSuit());
  }

  @JsonProperty("winner")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public PlayedCard winner() {
    if (isComplete()) {
      return getWinner().orElse(null);
    }
    return null;
  }

  @JsonIgnore
  Optional<PlayedCard> getWinner() {
    return getTrump()
        .flatMap(
            suit -> cards.stream().filter(pc -> pc.card().getSuit().equals(suit)).max(BY_RANK));
  }

  @JsonIgnore
  Optional<String> getNextPlayer(final HeartsState gameState) {
    if (isComplete()) {
      return Optional.empty();
    }
    if (cards.isEmpty()) {
      return Optional.of(leader());
    }
    final PlayedCard prevCard = cards.get(cards.size() - 1);
    final String nextPlayer = gameState.getPlayerLeftOf(prevCard.player());
    return Optional.of(nextPlayer);
  }

  int getPoints() {
    return cards.stream().map(PlayedCard::card).mapToInt(HeartsGame::getPoints).sum();
  }
}
