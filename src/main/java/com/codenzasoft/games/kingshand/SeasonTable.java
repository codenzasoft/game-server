package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.List;

public class SeasonTable {
  public static int MAX_TABLE_SIZE = 5;

  public List<Card> cards;
  public Season season;

  public SeasonTable(Season season) {
    this.season = season;
    this.cards = new ArrayList<>();
  }

  public void addCard(Card card) {
    if (cards.size() >= MAX_TABLE_SIZE) {
      throw new IllegalStateException(season + " table is full");
    }

    cards.add(card);
  }

  public void removeCard(Card card) {
    cards.remove(card);
  }
}
