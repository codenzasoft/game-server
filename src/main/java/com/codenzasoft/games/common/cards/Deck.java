package com.codenzasoft.games.common.cards;

import com.codenzasoft.games.common.Player;

import java.util.*;

public class Deck {

  public static final class Builder {
    public Deck build() {
      List<Card> cards = new ArrayList<>();
      for (Suit suit : Suit.values()) {
        for (Rank rank : Rank.values()) {
          cards.add(new Card(suit, rank));
        }
      }
      return new Deck(cards);
    }
  }

  private List<Card> cards;

  public Deck(List<Card> cards) {
    this.cards = cards;
  }

  public List<Card> getCards() {
    return new ArrayList<>(cards);
  }

  public void shuffle() {
    List<Card> shuffled = new ArrayList<>();
    Random random = new Random();
    while (!cards.isEmpty()) {
      shuffled.add(cards.remove(random.nextInt(cards.size())));
    }
    this.cards = shuffled;
  }

  public Map<Player, List<Card>> dealAll(final List<Player> players) {
    if (players.isEmpty()) {
      return Map.of();
    }
    Map<Player, List<Card>> hands = new HashMap<>();
    for (Player player : players) {
      hands.put(player, new ArrayList<>());
    }

    int playerIndex = 0;
    while (!cards.isEmpty()) {
      final Player player = players.get(playerIndex);
      final Card card = cards.remove(0);
      final List<Card> hand = hands.get(player);
      hand.add(card);
      playerIndex++;
      if (playerIndex >= players.size()) {
        playerIndex = 0;
      }
    }
    return hands;
  }
}
