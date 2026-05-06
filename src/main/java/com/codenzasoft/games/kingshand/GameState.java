package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameState {

  public static final int MAX_TABLE_SIZE = 5;

  private Map<Season, List<Card>> board;
  private double population;
  private double production;
  private double food;
  private Season curSeason;
  private Phase curPhase;
  private int year;

  private GameState(Builder b) {
    this.board = b.board;
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
    private double population = 5.0;
    private double production = 100.0;
    private double food = 50.0;
    private Season curSeason = Season.SPRING;
    private Phase curPhase = Phase.ADD;
    private int year = 1;

    private Builder() {
      board.put(Season.SPRING, new ArrayList<>());
      board.put(Season.SUMMER, new ArrayList<>());
      board.put(Season.FALL, new ArrayList<>());
      board.put(Season.WINTER, new ArrayList<>());
    }

    public Builder table(Season season, List<Card> cards) {
      board.put(season, new ArrayList<>(cards));
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
