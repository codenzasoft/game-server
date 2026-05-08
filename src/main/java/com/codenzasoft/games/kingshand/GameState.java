package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure data carrier for a King's Hand game. All rule-based mutations live in {@link GameEngine};
 * this class only holds fields, exposes getters/setters, and validates static invariants on
 * construction via its {@link Builder}.
 */
public class GameState {

  // per season
  private Map<Season, List<Card>> board;
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

  // ----- Getters -----

  public Map<Season, List<Card>> getBoard() {
    return board;
  }

  /** Convenience accessor for the table belonging to the current season. */
  public List<Card> getCurrentTable() {
    return board.get(curSeason);
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
        discardPile.put(season, new ArrayList<>());
        drawPile.put(season, new ArrayList<>());
      }
    }

    public Builder table(Season season, List<Card> cards) {
      board.put(season, new ArrayList<>(cards));
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
