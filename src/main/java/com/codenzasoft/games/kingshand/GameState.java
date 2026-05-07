package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameState {

  public static final int MAX_TABLE_SIZE = 5;

  // per season
  private Map<Season, List<Card>> board;
  private Map<Season, List<Card>> deck;
  private Map<Season, List<Card>> drawPile;
  private Map<Season, List<Card>> discardPile;
  // current hand
  private List<Card> hand;
  // resources
  private double population;
  private double production;
  private double food;
  // where are we
  private Season curSeason;
  private Phase curPhase;
  private int year;

  private GameState(Builder b) {
    this.board = b.board;
    this.deck = b.deck;
    this.drawPile = b.drawPile;
    this.discardPile = b.discardPile;
    this.hand = b.hand;
    this.population = b.population;
    this.production = b.production;
    this.food = b.food;
    this.curSeason = b.curSeason;
    this.curPhase = b.curPhase;
    this.year = b.year;
  }

  // Creates a fresh game state: empty season tables, starting population/production, spring of year
  // 1
  public static GameState newState() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public synchronized void advancePhase() {
    // if play phase ends, move to the next season
    if (curPhase == Phase.PLAY) {
      advanceSeason();
    }

    curPhase = curPhase.next();
  }

  public synchronized void advanceSeason() {
    // new year if we end winter
    if (curSeason == Season.WINTER) {
      year++;
    }

    curSeason = curSeason.next();

    triggerCurrentSeasonTable();
  }

  public List<Card> getCurrentTable() {
    return board.get(curSeason);
  }

  public synchronized void addCard(Season season, Card card) {
    List<Card> table = board.get(season);
    if (table.size() >= MAX_TABLE_SIZE) {
      throw new IllegalStateException(season + " table is full");
    }
    table.add(card);
  }

  public synchronized void removeCard(Season season, Card card) {
    board.get(season).remove(card);
  }

  private synchronized void triggerCurrentSeasonTable() {
    List<Card> table = getCurrentTable();

    // Walk every effect on every card on the current season's table. For now, only RECURRING
    // effects targeting a global resource (population / production / food) fire here. SELF and
    // CARD targets — and SINGLE-frequency effects — are handled by the end-of-PLAY resolver
    // landing in a later PR.
    for (Card card : table) {
      for (Effect effect : card.effects()) {
        if (effect.frequencyType() != FrequencyType.RECURRING) {
          continue;
        }
        if (effect.target() instanceof ResourceTarget rt) {
          switch (rt) {
            case POPULATION -> population = effect.nextValue(population);
            case PRODUCTION -> production = effect.nextValue(production);
            case FOOD -> food = effect.nextValue(food);
          }
        }
      }
    }
  }

  // when we add a card to one of our decks, shuffle the deck.
  public void addToDeck(Season season, Card card) {
    if (season == null) {
      throw new IllegalStateException("season cannot be null");
    }
    if (card == null) {
      throw new IllegalStateException("card cannot be null");
    }

    deck.get(season).add(card);
    Collections.shuffle(deck.get(season));
  }

  //add multiple cards and shuffle.
  public void addToDeck(Season season, List<Card> cards) {
    if (season == null) {
      throw new IllegalStateException("season cannot be null");
    }
    if (cards == null) {
      throw new IllegalStateException("cards cannot be null");
    }
  
    deck.get(season).addAll(cards);
    Collections.shuffle(deck.get(season));
  }

  // draw n cards, if drawpile empty, shuffle discard into draw.
  public List<Card> draw(Season season, int n) {
    if (season == null) {
      throw new IllegalStateException("season cannot be null");
    }
    if (n < 0) {
      throw new IllegalStateException("cannot draw a negative number of cards");
    }

    List<Card> drawnCards = new ArrayList<>();

    for (int i = 0; i < n; i++) {
      if (drawPile.get(season).isEmpty()) {
        reshuffleDiscardIntoDrawPile(season);
      }

      if (drawPile.get(season).isEmpty()) {
        break;
      }

      Card drawnCard = drawPile.get(season).remove(0);
      hand.add(drawnCard);
      drawnCards.add(drawnCard);
    }

    return drawnCards;
  }

  // move discard to draw pile, clear discard pile, shuffle draw pile.
  private void reshuffleDiscardIntoDrawPile(Season season) {

    drawPile.get(season).addAll(discardPile.get(season));
    discardPile.get(season).clear();
    Collections.shuffle(drawPile.get(season));
  }

  // move hand to discrad pile, clear hand.
  public void discardHand(Season season) {
    if (season == null) {
      throw new IllegalStateException("season cannot be null");
    }

    discardPile.get(season).addAll(hand);
    hand.clear();
  }

  // ----- Getters -----

  public Map<Season, List<Card>> getBoard() {
    return board;
  }

  public Map<Season, List<Card>> getDeck() {
    return deck;
  }

  public Map<Season, List<Card>> getDiscardPile() {
    return discardPile;
  }

  public Map<Season, List<Card>> getDrawPile() {
    return drawPile;
  }

  public List<Card> getHand() {
    return hand;
  }

  public double getPopulation() {
    return population;
  }

  public double getProduction() {
    return production;
  }

  public double getFood() {
    return food;
  }

  public Season getCurSeason() {
    return curSeason;
  }

  public Phase getCurPhase() {
    return curPhase;
  }

  public double getYear() {
    return year;
  }

  // ----- Setters -----

  public void setBoard(Map<Season, List<Card>> board) {
    this.board = board;
  }

  public void setDeck(Map<Season, List<Card>> deck) {
    this.deck = deck;
  }

  public void setDiscardPile(Map<Season, List<Card>> discardPile) {
    this.discardPile = discardPile;
  }

  public void setDrawPile(Map<Season, List<Card>> drawPile) {
    this.drawPile = drawPile;
  }

  public void setHand(List<Card> hand) {
    this.hand = hand;
  }

  public void setPopulation(double population) {
    this.population = population;
  }

  public void setProduction(double production) {
    this.production = production;
  }

  public void setFood(double food) {
    this.food = food;
  }

  public void setCurSeason(Season curSeason) {
    this.curSeason = curSeason;
  }

  public void setCurPhase(Phase curPhase) {
    this.curPhase = curPhase;
  }

  public void setYear(int year) {
    this.year = year;
  }

  // ----- Builder -----

  public static final class Builder {
    private Map<Season, List<Card>> board = new HashMap<>();
    private Map<Season, List<Card>> deck = new HashMap<>();
    private Map<Season, List<Card>> discardPile = new HashMap<>();
    private Map<Season, List<Card>> drawPile = new HashMap<>();
    private List<Card> hand = new ArrayList<>();
    private double population = 5.0;
    private double production = 100.0;
    private double food = 50.0;
    private Season curSeason = Season.SPRING;
    private Phase curPhase = Phase.ADD;
    private int year = 1;

    // fill similar data types with seasons
    private Builder() {
      for (Season season : Season.values()) {

        board.put(season, new ArrayList<>());
        deck.put(season, new ArrayList<>());
        discardPile.put(season, new ArrayList<>());
        drawPile.put(season, new ArrayList<>());
      }
    }

    public Builder table(Season season, List<Card> cards) {
      board.put(season, new ArrayList<>(cards));
      return this;
    }

    public Builder deck(Season season, List<Card> cards) {
      deck.put(season, new ArrayList<>(cards));
      return this;
    }

    public Builder discardPile(Season season, List<Card> cards) {
      discardPile.put(season, new ArrayList<>(cards));
      return this;
    }

    public Builder drawPile(Season season, List<Card> cards) {
      drawPile.put(season, new ArrayList<>(cards));
      return this;
    }

    public Builder hand(List<Card> cards) {
      this.hand = new ArrayList<>(cards);
      return this;
    }

    public Builder population(double population) {
      this.population = population;
      return this;
    }

    public Builder production(double production) {
      this.production = production;
      return this;
    }

    public Builder food(double food) {
      this.food = food;
      return this;
    }

    public Builder curSeason(Season curSeason) {
      this.curSeason = curSeason;
      return this;
    }

    public Builder curPhase(Phase curPhase) {
      this.curPhase = curPhase;
      return this;
    }

    public Builder year(int year) {
      this.year = year;
      return this;
    }

    public GameState build() {
      if (curSeason == null) {
        throw new IllegalStateException("missing current season");
      }
      if (curPhase == null) {
        throw new IllegalStateException("missing current phase");
      }
      for (Season s : Season.values()) {
        if (!board.containsKey(s)) {
          throw new IllegalStateException("missing table for season " + s.name());
        }
      }
      if (population < 0.0) {
        throw new IllegalStateException("population cannot be negative");
      }
      if (production < 0.0) {
        throw new IllegalStateException("production cannot be negative");
      }
      if (food < 0.0) {
        throw new IllegalStateException("food cannot be negative");
      }
      if (year < 0) {
        throw new IllegalStateException("year cannot be negative");
      }
      return new GameState(this);
    }
  }
}
