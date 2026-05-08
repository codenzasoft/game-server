package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns all rule-based mutations of a {@link GameState}: phase/season advancement, table mutations,
 * the per-season card-trigger pass, and draw/discard/hand pile mutations. {@code GameState} is a
 * passive data carrier; this engine is the only place that should mutate it.
 */
public class GameEngine {

  public static final int MAX_TABLE_SIZE = 5;

  private final GameState state;

  public GameEngine(GameState state) {
    if (state == null) {
      throw new IllegalArgumentException("state cannot be null");
    }
    this.state = state;
  }

  public GameState getState() {
    return state;
  }

  // ----- Phase / season progression -----

  public synchronized void advancePhase() {
    // if play phase ends, move to the next season
    if (state.getCurPhase() == Phase.PLAY) {
      advanceSeason();
    }

    state.setCurPhase(state.getCurPhase().next());
  }

  public synchronized void advanceSeason() {
    // new year if we end winter
    if (state.getCurSeason() == Season.WINTER) {
      state.setYear((int) state.getYear() + 1);
    }

    state.setCurSeason(state.getCurSeason().next());

    triggerCurrentSeasonTable();
  }

  // ----- Table mutations -----

  public synchronized void addCard(Season season, Card card) {
    List<Card> table = state.getBoard().get(season);
    if (table.size() >= MAX_TABLE_SIZE) {
      throw new IllegalStateException(season + " table is full");
    }
    table.add(card);
  }

  public synchronized void removeCard(Season season, Card card) {
    state.getBoard().get(season).remove(card);
  }

  // ----- Season trigger -----

  private synchronized void triggerCurrentSeasonTable() {
    List<Card> table = state.getCurrentTable();

    // Walk every effect on every card on the current season's table. For now, only RECURRING
    // effects targeting a global resource (population / production / food) fire here. SELF and
    // CARD targets — and SINGLE-frequency effects — are handled by the end-of-PLAY resolver
    // landing in a later PR.
    for (Card card : table) {
      for (Effect effect : card.effects()) {

        if (effect.frequencyType() == FrequencyType.RECURRING) {

          if (effect.target() instanceof ResourceTarget rt) {
            switch (rt) {
              case POPULATION -> state.setPopulation(effect.apply(state.getPopulation()));
              case PRODUCTION -> state.setProduction(effect.apply(state.getProduction()));
              case FOOD -> state.setFood(effect.apply(state.getFood()));
            }
          }
        }
      }
    }
  }

  // ----- Pile mutations -----

  // when we add a card to one of our draws, add it to the drawpile and shuffle the drawpile.
  public synchronized void addToDeck(Season season, Card card) {
    addToDeck(season, List.of(card));
  }

  // add multiple cards and shuffle.
  public synchronized void addToDeck(Season season, List<Card> cards) {
    if (season == null) {
      throw new IllegalStateException("season cannot be null");
    }
    if (cards == null) {
      throw new IllegalStateException("cards cannot be null");
    }

    state.getDrawPile().get(season).addAll(cards);
    Collections.shuffle(state.getDrawPile().get(season));
  }

  // draw n cards, if drawpile empty, shuffle discard into draw.
  public synchronized List<Card> draw(Season season, int n) {
    if (season == null) {
      throw new IllegalStateException("season cannot be null");
    }
    if (n < 0) {
      throw new IllegalStateException("cannot draw a negative number of cards");
    }

    List<Card> drawnCards = new ArrayList<>();

    for (int i = 0; i < n; i++) {
      if (state.getDrawPile().get(season).isEmpty()) {
        reshuffleDiscardIntoDrawPile(season);
      }

      if (!state.getDrawPile().get(season).isEmpty()) {

        Card drawnCard = state.getDrawPile().get(season).remove(0);
        state.getHand().add(drawnCard);
        drawnCards.add(drawnCard);
      }
    }

    return drawnCards;
  }

  // move discard to draw pile, clear discard pile, shuffle draw pile.
  private synchronized void reshuffleDiscardIntoDrawPile(Season season) {
    state.getDrawPile().get(season).addAll(state.getDiscardPile().get(season));
    state.getDiscardPile().get(season).clear();
    Collections.shuffle(state.getDrawPile().get(season));
  }

  // move hand to discard pile, clear hand.
  public synchronized void discardHand(Season season) {
    if (season == null) {
      throw new IllegalStateException("season cannot be null");
    }

    state.getDiscardPile().get(season).addAll(state.getHand());
    state.getHand().clear();
  }
}
