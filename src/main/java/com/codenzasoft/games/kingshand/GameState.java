package com.codenzasoft.games.kingshand;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
  // true once the player's population has reached zero; no further actions are possible
  private boolean gameOver;
  // staffing snapshot for the current season's table — computed by GameEngine during PLAY phase
  private List<CardStaffing> tableStaffing;
  // per-card resolution log from the most recent endAndRun call — empty between seasons
  private List<ResolutionStep> resolutionLog;
  // new resource types — both start locked at 0
  private double gold;
  private double strength;
  // resources currently usable; population/production/food/populationCap are always present,
  // gold/strength are added once TREASURY/BARRACKS complete. A set (rather than one boolean per
  // resource) so future unlockable resources don't need a new field.
  private Set<ResourceTarget> unlockedResources;
  // events emitted during the last endAndRun (e.g. "TREASURY_COMPLETED")
  private List<String> greatWorkLog;
  // ----- Council / Advisors -----
  // hired advisors currently seated on the council; max GameEngine.MAX_COUNCIL_SIZE (3)
  private List<SeatedAdvisor> council;
  // candidate advisors offered during the current COUNCIL_ADD phase; empty outside that phase
  private List<Advisor> councilOfferChoices;
  // the single rotating Reform/Decree proposal for the current COUNCIL_MEETING phase, or null if no
  // seated advisor currently has anything left to propose
  private CouncilProposal currentProposal;
  // cumulative permanent free-reroll count granted by Decrees (Aldwin's "Found the Town Hall",
  // Varn's "Plant an Informant") — added on top of the base 1 free reroll every ADD phase already
  // gets, regardless of whether the granting advisor is still seated
  private int permanentFreeRerolls;
  // names of Great Works that have already received Tomas Ferro's "Expedite Construction" Decree;
  // prevents that Decree from being used more than once on the same distinct Great Work
  private Set<String> acceleratedGreatWorkNames;

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
    this.gameOver = b.gameOver;
    this.tableStaffing = b.tableStaffing;
    this.resolutionLog = b.resolutionLog;
    this.gold = b.gold;
    this.strength = b.strength;
    this.unlockedResources = b.unlockedResources;
    this.greatWorkLog = b.greatWorkLog;
    this.council = b.council;
    this.councilOfferChoices = b.councilOfferChoices;
    this.currentProposal = b.currentProposal;
    this.permanentFreeRerolls = b.permanentFreeRerolls;
    this.acceleratedGreatWorkNames = b.acceleratedGreatWorkNames;
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

  /**
   * Returns {@code true} if a card named {@code name} exists anywhere in the game — the hand, any
   * season's table, draw pile, or discard pile. Used to detect whether a Great Work has already
   * been drafted, commissioned, or completed, since each Great Work may only ever exist once.
   */
  public boolean isCardInPlay(String name) {
    return isCardInPlay(
        name, List.of(hand), board.values(), drawPile.values(), discardPile.values());
  }

  /** Returns {@code true} if {@code card} (matched by name) exists anywhere in the game. */
  public boolean isCardInPlay(Card card) {
    return isCardInPlay(card.name());
  }

  @SafeVarargs
  private boolean isCardInPlay(final String name, final Collection<List<Card>>... collections) {
    for (Collection<List<Card>> lists : collections) {
      for (List<Card> list : lists) {
        if (list.stream().anyMatch(c -> c.name().equals(name))) return true;
      }
    }
    return false;
  }

  @JsonProperty("population")
  public double getPopulation() {
    return population;
  }

  /**
   * The maximum population allowed. Starts at 100 and is permanently raised whenever a housing card
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
   * seasons (during ADD, COUNCIL_ADD, and COUNCIL_MEETING phases). The client uses this to drive
   * indicators and toasts without re-simulating game logic.
   */
  @JsonProperty("resolutionLog")
  public List<ResolutionStep> getResolutionLog() {
    return resolutionLog == null ? List.of() : resolutionLog;
  }

  public void setResolutionLog(List<ResolutionStep> log) {
    this.resolutionLog = log == null ? List.of() : List.copyOf(log);
  }

  @JsonProperty("gold")
  public double getGold() {
    return gold;
  }

  public void setGold(double gold) {
    this.gold = gold;
  }

  @JsonProperty("strength")
  public double getStrength() {
    return strength;
  }

  public void setStrength(double strength) {
    this.strength = strength;
  }

  /**
   * The set of resources currently usable. Population, production, and food are always present;
   * gold and strength are added once the TREASURY/BARRACKS Great Works complete, respectively.
   */
  @JsonProperty("unlockedResources")
  public Set<ResourceTarget> getUnlockedResources() {
    return unlockedResources;
  }

  /** Permanently unlocks every resource {@code card} unlocks (e.g. on Great Work completion). */
  public void unlock(Card card) {
    unlockedResources.addAll(card.unlockedResources());
  }

  /**
   * Returns {@code true} if every {@link ResourceTarget} referenced by {@code card}'s effects is
   * currently unlocked. Used to gate the ADD-phase offer pool and Great Work commissioning.
   */
  public boolean areRequiredResourcesUnlocked(Card card) {
    return unlockedResources.containsAll(card.requiredResources());
  }

  /**
   * Returns {@code true} if applying every {@link ResourceTarget} effect in {@code effects} would
   * leave production, food, gold, and strength all {@code >= 0}. Used to check whether a Great
   * Work's per-season construction cost is currently affordable.
   *
   * <p>Only {@link ResourceTarget} effects are evaluated here; {@link CardTarget} effects are
   * ignored (they don't appear on construction cards), as are POPULATION/POPULATION_CAP effects
   * (not construction costs).
   */
  public boolean canAfford(List<Effect> effects) {
    double simProd = production;
    double simFood = food;
    double simGold = gold;
    double simStrength = strength;

    for (Effect e : effects) {
      if (e.target() instanceof ResourceTarget rt) {
        switch (rt) {
          case PRODUCTION -> simProd = e.apply(simProd);
          case FOOD -> simFood = e.apply(simFood);
          case GOLD -> simGold = e.apply(simGold);
          case STRENGTH -> simStrength = e.apply(simStrength);
          default -> {} // POPULATION / POPULATION_CAP are not construction costs
        }
      }
    }

    return simProd >= 0 && simFood >= 0 && simGold >= 0 && simStrength >= 0;
  }

  @JsonProperty("goldUnlocked")
  public boolean isGoldUnlocked() {
    return unlockedResources.contains(ResourceTarget.GOLD);
  }

  @JsonProperty("strengthUnlocked")
  public boolean isStrengthUnlocked() {
    return unlockedResources.contains(ResourceTarget.STRENGTH);
  }

  /**
   * Returns {@code true} once every win-condition Great Work has completed — i.e. the board
   * contains a completed (non-under-construction) card with {@link Card#victory()} {@code == true}
   * for each of the four seasonal win conditions. Computed dynamically from the board rather than
   * cached, since a completed Great Work's presence there is itself the source of truth.
   */
  public boolean isVictoryComplete() {
    long completedVictoryCards =
        board.values().stream()
            .flatMap(List::stream)
            .filter(c -> c.victory() && !c.isGreatWork())
            .count();
    long totalVictoryCards = CardCatalog.ALL_GREAT_WORKS.stream().filter(Card::victory).count();
    return completedVictoryCards >= totalVictoryCards;
  }

  /**
   * {@code true} once all four win-condition Great Works are completed. Alias for {@link
   * #isVictoryComplete()} kept under this name for the {@code gameWon} JSON wire property and
   * existing call sites.
   */
  @JsonProperty("gameWon")
  public boolean isGameWon() {
    return isVictoryComplete();
  }

  /**
   * Events emitted during the most recent {@link Phase#PLAY} resolution relating to Great Works
   * (e.g. {@code "TREASURY_COMPLETED"}, {@code "THE_GREAT_RESERVOIR_PERIOD_DECREMENTED:5"}). Empty
   * between seasons. Cleared at the start of each {@code endAndRun}.
   */
  @JsonProperty("greatWorkLog")
  public List<String> getGreatWorkLog() {
    return greatWorkLog == null ? List.of() : greatWorkLog;
  }

  public void setGreatWorkLog(List<String> log) {
    this.greatWorkLog = log == null ? List.of() : List.copyOf(log);
  }

  /** Advisors currently seated on the council; never more than {@code MAX_COUNCIL_SIZE} (3). */
  @JsonProperty("council")
  public List<SeatedAdvisor> getCouncil() {
    return council == null ? List.of() : council;
  }

  /** Seats {@code seated} onto the council. Caller is responsible for the seat-count check. */
  public void addToCouncil(SeatedAdvisor seated) {
    council.add(seated);
  }

  /** Returns the seated advisor whose {@code advisorId} equals {@code advisorId}, or {@code null}. */
  public SeatedAdvisor findSeatedAdvisor(String advisorId) {
    return council.stream().filter(s -> s.getAdvisorId().equals(advisorId)).findFirst().orElse(null);
  }

  /**
   * Candidate advisors offered during the current {@link Phase#COUNCIL_ADD} phase; empty outside
   * that phase.
   */
  @JsonProperty("councilOfferChoices")
  public List<Advisor> getCouncilOfferChoices() {
    return councilOfferChoices == null ? List.of() : councilOfferChoices;
  }

  public void setCouncilOfferChoices(List<Advisor> councilOfferChoices) {
    this.councilOfferChoices = councilOfferChoices == null ? List.of() : List.copyOf(councilOfferChoices);
  }

  /**
   * The single rotating Reform/Decree proposal for the current {@link Phase#COUNCIL_MEETING}
   * phase, or {@code null} (omitted from JSON) if no seated advisor currently has anything left to
   * propose.
   */
  @JsonProperty("currentProposal")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public CouncilProposal getCurrentProposal() {
    return currentProposal;
  }

  public void setCurrentProposal(CouncilProposal currentProposal) {
    this.currentProposal = currentProposal;
  }

  /**
   * Cumulative permanent free-reroll count granted by Decrees (Reeve Aldwin's "Found the Town
   * Hall", Lady Iseult Varn's "Plant an Informant"). Stacks across advisors and survives them being
   * removed from the council (a Decree, once enacted, is a permanent civic change).
   */
  @JsonProperty("permanentFreeRerolls")
  public int getPermanentFreeRerolls() {
    return permanentFreeRerolls;
  }

  public void setPermanentFreeRerolls(int permanentFreeRerolls) {
    this.permanentFreeRerolls = permanentFreeRerolls;
  }

  public void incrementPermanentFreeRerolls(int delta) {
    this.permanentFreeRerolls += delta;
  }

  /**
   * Names of Great Works that have already received Tomas Ferro's "Expedite Construction" Decree.
   * Prevents that Decree from accelerating the same distinct Great Work more than once, while still
   * allowing it to recur across different Great Works over the course of a run.
   */
  @JsonProperty("acceleratedGreatWorkNames")
  public Set<String> getAcceleratedGreatWorkNames() {
    return acceleratedGreatWorkNames == null ? Set.of() : acceleratedGreatWorkNames;
  }

  public void markGreatWorkAccelerated(String greatWorkName) {
    acceleratedGreatWorkNames.add(greatWorkName);
  }

  // ----- Builder -----

  public static final class Builder {
    private Map<Season, List<Card>> board = new HashMap<>();
    private Map<Season, List<Card>> discardPile = new HashMap<>();
    private Map<Season, List<Card>> drawPile = new HashMap<>();
    private List<Card> hand = new ArrayList<>();
    private double population = 100.0;
    private double populationCap = 100.0;
    private double production = 1000.0;
    private double food = 750.0;
    private Season curSeason = Season.SPRING;
    private Phase curPhase = Phase.ADD;
    private int year = 1;
    private List<Card> addChoices = new ArrayList<>();
    private int rerollCount = 0;
    private boolean gameOver = false;
    private List<CardStaffing> tableStaffing = List.of();
    private List<ResolutionStep> resolutionLog = List.of();
    private double gold = 0.0;
    private double strength = 0.0;
    private Set<ResourceTarget> unlockedResources =
        EnumSet.of(
            ResourceTarget.POPULATION,
            ResourceTarget.PRODUCTION,
            ResourceTarget.FOOD,
            ResourceTarget.POPULATION_CAP);
    private List<String> greatWorkLog = List.of();
    private List<SeatedAdvisor> council = new ArrayList<>();
    private List<Advisor> councilOfferChoices = new ArrayList<>();
    private CouncilProposal currentProposal = null;
    private int permanentFreeRerolls = 0;
    private Set<String> acceleratedGreatWorkNames = new HashSet<>();

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

    public Builder gold(double gold) {
      this.gold = gold;
      return this;
    }

    public Builder strength(double strength) {
      this.strength = strength;
      return this;
    }

    public Builder goldUnlocked(boolean goldUnlocked) {
      if (goldUnlocked) {
        this.unlockedResources.add(ResourceTarget.GOLD);
      } else {
        this.unlockedResources.remove(ResourceTarget.GOLD);
      }
      return this;
    }

    public Builder strengthUnlocked(boolean strengthUnlocked) {
      if (strengthUnlocked) {
        this.unlockedResources.add(ResourceTarget.STRENGTH);
      } else {
        this.unlockedResources.remove(ResourceTarget.STRENGTH);
      }
      return this;
    }

    public Builder unlockedResources(Set<ResourceTarget> unlockedResources) {
      this.unlockedResources =
          unlockedResources == null
              ? EnumSet.noneOf(ResourceTarget.class)
              : EnumSet.copyOf(unlockedResources);
      return this;
    }

    public Builder greatWorkLog(List<String> greatWorkLog) {
      this.greatWorkLog = greatWorkLog == null ? List.of() : new ArrayList<>(greatWorkLog);
      return this;
    }

    public Builder council(List<SeatedAdvisor> council) {
      this.council = council == null ? new ArrayList<>() : new ArrayList<>(council);
      return this;
    }

    public Builder councilOfferChoices(List<Advisor> councilOfferChoices) {
      this.councilOfferChoices =
          councilOfferChoices == null ? new ArrayList<>() : new ArrayList<>(councilOfferChoices);
      return this;
    }

    public Builder currentProposal(CouncilProposal currentProposal) {
      this.currentProposal = currentProposal;
      return this;
    }

    public Builder permanentFreeRerolls(int permanentFreeRerolls) {
      this.permanentFreeRerolls = permanentFreeRerolls;
      return this;
    }

    public Builder acceleratedGreatWorkNames(Set<String> acceleratedGreatWorkNames) {
      this.acceleratedGreatWorkNames =
          acceleratedGreatWorkNames == null ? new HashSet<>() : new HashSet<>(acceleratedGreatWorkNames);
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
