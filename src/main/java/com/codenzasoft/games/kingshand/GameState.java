package com.codenzasoft.games.kingshand;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
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
  private double populationCap;
  private double production;
  private double food;
  // where are we
  private Season curSeason;
  private Phase curPhase;
  private int year;
  // ADD-phase offer state
  private List<Card> addChoices;
  private int rerollCount;
  // the event rolled for the current RANDOM_EVENT phase (null between phases)
  private Event currentEvent;
  // true once the player's population has reached zero; no further actions are possible
  private boolean gameOver;
  // staffing snapshot for the current season's table — computed by GameEngine during PLAY phase
  private List<CardStaffing> tableStaffing;
  // per-card resolution log from the most recent endAndRun call — empty between seasons
  private List<ResolutionStep> resolutionLog;

  private GameState(Builder b) {
    this.board = b.board;
    this.drawPile = b.drawPile;
    this.discardPile = b.discardPile;
    this.hand = b.hand;
    this.population = b.population;
    this.populationCap = b.populationCap;
    this.production = b.production;
    this.food = b.food;
    this.curSeason = b.curSeason;
    this.curPhase = b.curPhase;
    this.year = b.year;
    this.addChoices = b.addChoices;
    this.rerollCount = b.rerollCount;
    this.currentEvent = b.currentEvent;
    this.gameOver = b.gameOver;
    this.tableStaffing = b.tableStaffing;
    this.resolutionLog = b.resolutionLog;
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

  @JsonProperty("board")
  public Map<Season, List<Card>> getBoard() {
    return board;
  }

  /** Convenience accessor for the table belonging to the current season. */
  public List<Card> getCurrentTable() {
    return board.get(curSeason);
  }

  /**
   * Returns {@code true} if the current season's table contains a card whose name matches {@code
   * card}'s name. Matching by name (rather than full record equality) ensures that an
   * engine-boosted table card — whose effect modifiers differ from the hand card — is still
   * recognised as the upgrade target.
   */
  public boolean tableContains(Card card) {
    return getCurrentTable().stream().anyMatch(c -> c.name().equals(card.name()));
  }

  /**
   * Returns the first table card whose name equals {@code name}, or {@code null} if none exists.
   * Used by the upgrade path to retrieve the actual (potentially boosted) table card before
   * merging.
   */
  public Card findTableCard(String name) {
    return getCurrentTable().stream().filter(c -> c.name().equals(name)).findFirst().orElse(null);
  }

  /**
   * Replaces the first occurrence of {@code oldCard} on the current season's table with {@code
   * newCard}.
   *
   * @throws IllegalArgumentException if {@code oldCard} is not present on the table
   */
  public void replaceTableCard(Card oldCard, Card newCard) {
    List<Card> table = getCurrentTable();
    int index = table.indexOf(oldCard);
    if (index == -1) {
      throw new IllegalArgumentException("card not found on current table");
    }
    table.set(index, newCard);
  }

  /** Appends {@code card} to the current season's table. */
  public void addToTable(Card card) {
    getCurrentTable().add(card);
  }

  /** Removes the first occurrence of {@code card} from the current season's table. */
  public void removeFromTable(Card card) {
    getCurrentTable().remove(card);
  }

  /**
   * Removes the card at {@code slot} from the current season's table.
   *
   * @throws IndexOutOfBoundsException if {@code slot} is out of range
   */
  public void removeFromTableAt(int slot) {
    getCurrentTable().remove(slot);
  }

  /**
   * Removes {@code card} from the current season's table and adds it to the current season's
   * discard pile in one step.
   */
  public void discardFromTable(Card card) {
    removeFromTable(card);
    getDiscardPile().get(curSeason).add(card);
  }

  /**
   * Moves every card in the hand to the current season's discard pile and clears the hand. Uses
   * {@link #getCurSeason()} so callers don't need to pass the season explicitly.
   */
  public void discardHand() {
    getDiscardPile().get(curSeason).addAll(hand);
    hand.clear();
  }

  @JsonProperty("discardPile")
  public Map<Season, List<Card>> getDiscardPile() {
    return discardPile;
  }

  @JsonProperty("drawPile")
  public Map<Season, List<Card>> getDrawPile() {
    return drawPile;
  }

  @JsonProperty("hand")
  public List<Card> getHand() {
    return hand;
  }

  @JsonProperty("population")
  public double getPopulation() {
    return population;
  }

  /**
   * The maximum population allowed. Starts at 20 and is permanently raised whenever a housing card
   * (e.g. BUNKHOUSE, MANOR) fires its {@link ResourceTarget#POPULATION_CAP} effect during {@link
   * Phase#PLAY} resolution.
   */
  @JsonProperty("populationCap")
  public double getPopulationCap() {
    return populationCap;
  }

  public void setPopulationCap(double populationCap) {
    this.populationCap = populationCap;
  }

  @JsonProperty("production")
  public double getProduction() {
    return production;
  }

  @JsonProperty("food")
  public double getFood() {
    return food;
  }

  @JsonProperty("curSeason")
  public Season getCurSeason() {
    return curSeason;
  }

  @JsonProperty("curPhase")
  public Phase getCurPhase() {
    return curPhase;
  }

  @JsonProperty("year")
  public int getYear() {
    return year;
  }

  @JsonProperty("addChoices")
  public List<Card> getAddChoices() {
    return addChoices;
  }

  @JsonProperty("rerollCount")
  public int getRerollCount() {
    return rerollCount;
  }

  /**
   * The event rolled at the start of the current {@link Phase#RANDOM_EVENT} turn, or {@code null}
   * if no event has been rolled yet (or no eligible events existed).
   */
  @JsonProperty("currentEvent")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Event getCurrentEvent() {
    return currentEvent;
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

  public void setAddChoices(List<Card> addChoices) {
    this.addChoices = addChoices;
  }

  public void setRerollCount(int rerollCount) {
    this.rerollCount = rerollCount;
  }

  public void setCurrentEvent(Event currentEvent) {
    this.currentEvent = currentEvent;
  }

  @JsonProperty("gameOver")
  public boolean isGameOver() {
    return gameOver;
  }

  public void setGameOver(boolean gameOver) {
    this.gameOver = gameOver;
  }

  /**
   * Staffing snapshot for the current season's table, computed by {@link GameEngine} during the
   * {@link Phase#PLAY} phase. Index {@code i} corresponds to the card at slot {@code i} on the
   * table. Empty list outside of the PLAY phase.
   */
  @JsonProperty("tableStaffing")
  public List<CardStaffing> getTableStaffing() {
    return tableStaffing == null ? List.of() : tableStaffing;
  }

  public void setTableStaffing(List<CardStaffing> tableStaffing) {
    this.tableStaffing =
        tableStaffing == null
            ? List.of()
            : Collections.unmodifiableList(new ArrayList<>(tableStaffing));
  }

  /**
   * Per-card resolution log from the most recent {@link Phase#PLAY} end-and-run. Empty between
   * seasons (during ADD and RANDOM_EVENT phases). The client uses this to drive indicators and
   * toasts without re-simulating game logic.
   */
  @JsonProperty("resolutionLog")
  public List<ResolutionStep> getResolutionLog() {
    return resolutionLog == null ? List.of() : resolutionLog;
  }

  public void setResolutionLog(List<ResolutionStep> log) {
    this.resolutionLog = log == null ? List.of() : List.copyOf(log);
  }

  // ----- Builder -----

  public static final class Builder {
    private Map<Season, List<Card>> board = new HashMap<>();
    private Map<Season, List<Card>> discardPile = new HashMap<>();
    private Map<Season, List<Card>> drawPile = new HashMap<>();
    private List<Card> hand = new ArrayList<>();
    private double population = 20.0;
    private double populationCap = 20.0;
    private double production = 200.0;
    private double food = 150.0;
    private Season curSeason = Season.SPRING;
    private Phase curPhase = Phase.ADD;
    private int year = 1;
    private List<Card> addChoices = new ArrayList<>();
    private int rerollCount = 0;
    private Event currentEvent = null;
    private boolean gameOver = false;
    private List<CardStaffing> tableStaffing = List.of();
    private List<ResolutionStep> resolutionLog = List.of();

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

    public Builder populationCap(double populationCap) {
      this.populationCap = populationCap;
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

    public Builder addChoices(List<Card> addChoices) {
      this.addChoices = new ArrayList<>(addChoices);
      return this;
    }

    public Builder rerollCount(int rerollCount) {
      this.rerollCount = rerollCount;
      return this;
    }

    public Builder currentEvent(Event currentEvent) {
      this.currentEvent = currentEvent;
      return this;
    }

    public Builder gameOver(boolean gameOver) {
      this.gameOver = gameOver;
      return this;
    }

    public Builder tableStaffing(List<CardStaffing> tableStaffing) {
      this.tableStaffing = tableStaffing == null ? List.of() : new ArrayList<>(tableStaffing);
      return this;
    }

    public Builder resolutionLog(List<ResolutionStep> resolutionLog) {
      this.resolutionLog = resolutionLog == null ? List.of() : new ArrayList<>(resolutionLog);
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
