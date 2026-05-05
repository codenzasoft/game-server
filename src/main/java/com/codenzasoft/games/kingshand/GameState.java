package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class GameState {

  public static final int MAX_TABLE_SIZE = 5;

  private Map<Season, List<Card>> board;
  private double population;
  private double production;
  private Season curSeason;
  private Phase curPhase;
  private double year;

  private GameState(Builder b) {
    this.board = b.board;
    this.population = b.population;
    this.production = b.production;
    this.curSeason = b.curSeason;
    this.curPhase = b.curPhase;
    this.year = b.year;
  }


//Creates a fresh game state: empty season tables, starting population/production, spring of year 1
  public static GameState newGame() {
    Map<Season, List<Card>> board = new EnumMap<>(Season.class);
    board.put(Season.SPRING, new ArrayList<>());
    board.put(Season.SUMMER, new ArrayList<>());
    board.put(Season.FALL, new ArrayList<>());
    board.put(Season.WINTER, new ArrayList<>());

    return builder()
        .board(board)
        .population(5)
        .production(100)
        .curSeason(Season.SPRING)
        .curPhase(Phase.ADD)
        .year(1)
        .build();
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

  public void addCard(Season season, Card card) {
    List<Card> table = board.get(season);
    if (table.size() >= MAX_TABLE_SIZE) {
      throw new IllegalStateException(season + " table is full");
    }
    table.add(card);
  }

  public void removeCard(Season season, Card card) {
    board.get(season).remove(card);
  }

  private void triggerCurrentSeasonTable() {
    List<Card> table = getCurrentTable();

    // trigger all cards on the table and update population and production accordingly
    for (Card card : table) {
      if (card.populationEffect().frequencyType() == FrequencyType.RECURRING) {
        population = card.nextPopulation(population);
      }
      if (card.productionEffect().frequencyType() == FrequencyType.RECURRING) {
        production = card.nextProduction(production);
      }
    }
  }

  // ----- Getters -----

  public Map<Season, List<Card>> getBoard() {
    return board;
  }

  public double getPopulation() {
    return population;
  }

  public double getProduction() {
    return production;
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

  public void setPopulation(double population) {
    this.population = population;
  }

  public void setProduction(double production) {
    this.production = production;
  }

  public void setCurSeason(Season curSeason) {
    this.curSeason = curSeason;
  }

  public void setCurPhase(Phase curPhase) {
    this.curPhase = curPhase;
  }

  public void setYear(double year) {
    this.year = year;
  }

  // ----- Builder -----

  public static final class Builder {
    private Map<Season, List<Card>> board;
    private double population;
    private double production;
    private Season curSeason;
    private Phase curPhase;
    private double year;

    private Builder() {}

    public Builder board(Map<Season, List<Card>> board) {
      this.board = board;
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

    public Builder curSeason(Season curSeason) {
      this.curSeason = curSeason;
      return this;
    }

    public Builder curPhase(Phase curPhase) {
      this.curPhase = curPhase;
      return this;
    }

    public Builder year(double year) {
      this.year = year;
      return this;
    }

    public GameState build() {
      return new GameState(this);
    }
  }
}
