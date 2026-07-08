package com.codenzasoft.games.kingshand;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Match-level state for a King's Hand game: the shared clock ({@link TimeState}) plus the list of
 * per-player kingdoms ({@link PlayerState}). This is the top-level object the engine mutates and
 * the client is sent.
 *
 * <p><b>History.</b> This class used to hold a single kingdom's data directly. In the two-player
 * refactor it became the container: the per-player fields moved to {@link PlayerState} and the
 * clock moved here so both players share one authoritative season/phase/year. For a single-player
 * game {@code players} has exactly one element.
 *
 * <p><b>Backward compatibility.</b> Every accessor the old flat {@code GameState} exposed still
 * exists here, delegating to the <em>active</em> player ({@link #primary()}, currently seat 0) and
 * resolving "the current season" from the shared {@link TimeState}. This keeps {@link GameEngine},
 * {@link Card#play}, the notification/broadcast layer, and the existing test suite working
 * unchanged while the second seat and seat-aware engine paths are built out in later passes. The
 * only additions to the wire payload are the {@code time} object and the {@code players} array; the
 * previously flat properties (resources, board, hand, …) are still emitted for the active player,
 * so existing single-player clients are unaffected.
 */
public class GameState {

  /** Seat index of the primary/active player. Fixed at 0 until seat-aware paths are added. */
  public static final int PRIMARY_SEAT = 0;

  private final List<PlayerState> players;
  private TimeState time;

  private GameState(Builder b) {
    this.players = b.players;
    this.time = b.time;
  }

  /** Creates a fresh single-player match: one starting kingdom, spring of year 1, ADD phase. */
  public static GameState newState() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Wraps an already-built {@link PlayerState} and {@link TimeState} into a single-player match.
   */
  public static GameState singlePlayer(PlayerState player, TimeState time) {
    return multiPlayer(List.of(player), time);
  }

  /**
   * Creates a fresh {@code count}-player match: {@code count} independent starting kingdoms that
   * share one clock (Spring, year 1, ADD phase). Each kingdom is built separately so their piles,
   * boards, and hands are fully independent — mutating one seat never touches another.
   *
   * @throws IllegalArgumentException if {@code count < 1}
   */
  public static GameState newMultiplayer(int count) {
    if (count < 1) {
      throw new IllegalArgumentException("a match needs at least one player");
    }
    List<PlayerState> kingdoms = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      kingdoms.add(PlayerState.newPlayer());
    }
    return multiPlayer(kingdoms, new TimeState(Season.SPRING, Phase.ADD, 1));
  }

  /** Convenience factory for the common two-player case. See {@link #newMultiplayer(int)}. */
  public static GameState newTwoPlayerState() {
    return newMultiplayer(2);
  }

  /**
   * Wraps already-built kingdoms and a shared {@link TimeState} into a match. The {@code players}
   * list is defensively copied; the {@link PlayerState} instances are used as-is, so callers must
   * pass independent kingdoms (each with its own mutable collections).
   *
   * @throws IllegalArgumentException if {@code players} is null or empty
   */
  public static GameState multiPlayer(List<PlayerState> players, TimeState time) {
    if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("a match needs at least one player");
    }
    Builder b = new Builder();
    b.players = new ArrayList<>(players);
    b.time = time;
    return new GameState(b);
  }

  // ----- Match structure -----

  /** All player kingdoms in this match, ordered by seat. Single-player games have one element. */
  @JsonProperty("players")
  public List<PlayerState> getPlayers() {
    return players;
  }

  /** Returns the kingdom at {@code seat}. */
  public PlayerState getPlayer(int seat) {
    return players.get(seat);
  }

  /** The primary/active player kingdom (seat {@link #PRIMARY_SEAT}). */
  public PlayerState primary() {
    return players.get(PRIMARY_SEAT);
  }

  @JsonProperty("time")
  public TimeState getTime() {
    return time;
  }

  /**
   * Advances the shared clock, deciding the next phase from the active player's unlocked resources
   * (whether the Council opens after Winter). Seat-aware council rules for two players are handled
   * in a later pass.
   */
  public void advanceTime() {
    time = time.advance(primary().getResources());
  }

  private Season season() {
    return time.season();
  }

  // ----- Delegating accessors (active player) -----

  @JsonProperty("resources")
  public ResourceState getResources() {
    return primary().getResources();
  }

  @JsonProperty("board")
  public Map<Season, List<Card>> getBoard() {
    return primary().getBoard();
  }

  /** Convenience accessor for the active player's table in the current season. */
  public List<Card> getCurrentTable() {
    return primary().getTable(season());
  }

  public boolean tableContains(Card card) {
    return primary().tableContains(season(), card);
  }

  public Card findTableCard(String name) {
    return primary().findTableCard(season(), name);
  }

  public void replaceTableCard(Card oldCard, Card newCard) {
    primary().replaceTableCard(season(), oldCard, newCard);
  }

  public void addToTable(Card card) {
    primary().addToTable(season(), card);
  }

  public void removeFromTable(Card card) {
    primary().removeFromTable(season(), card);
  }

  public void removeFromTableAt(int slot) {
    primary().removeFromTableAt(season(), slot);
  }

  public void discardFromTable(Card card) {
    primary().discardFromTable(season(), card);
  }

  public void discardHand() {
    primary().discardHand(season());
  }

  @JsonProperty("discardPile")
  public Map<Season, List<Card>> getDiscardPile() {
    return primary().getDiscardPile();
  }

  @JsonProperty("drawPile")
  public Map<Season, List<Card>> getDrawPile() {
    return primary().getDrawPile();
  }

  @JsonProperty("hand")
  public List<Card> getHand() {
    return primary().getHand();
  }

  public boolean isCardInPlay(String name) {
    return primary().isCardInPlay(name);
  }

  public boolean isCardInPlay(Card card) {
    return primary().isCardInPlay(card);
  }

  @JsonProperty("addChoices")
  public List<Card> getAddChoices() {
    return primary().getAddChoices();
  }

  @JsonProperty("rerollCount")
  public int getRerollCount() {
    return primary().getRerollCount();
  }

  public void setAddChoices(List<Card> addChoices) {
    primary().setAddChoices(addChoices);
  }

  public void setRerollCount(int rerollCount) {
    primary().setRerollCount(rerollCount);
  }

  @JsonProperty("gameOver")
  public boolean isGameOver() {
    return primary().isGameOver();
  }

  /** Staffing for the active player's current-season table. */
  @JsonProperty("tableStaffing")
  public List<CardStaffing> getTableStaffing() {
    return primary().getTableStaffing(season());
  }

  @JsonProperty("resolutionLog")
  public List<ResolutionStep> getResolutionLog() {
    return primary().getResolutionLog();
  }

  public void setResolutionLog(List<ResolutionStep> log) {
    primary().setResolutionLog(log);
  }

  public void unlock(Card card) {
    primary().unlock(card);
  }

  public boolean areRequiredResourcesUnlocked(Card card) {
    return primary().areRequiredResourcesUnlocked(card);
  }

  public boolean canAfford(List<Effect> effects) {
    return primary().canAfford(effects);
  }

  public boolean isVictoryComplete() {
    return primary().isVictoryComplete();
  }

  @JsonProperty("gameWon")
  public boolean isGameWon() {
    return primary().isGameWon();
  }

  @JsonProperty("council")
  public List<SeatedAdvisor> getCouncil() {
    return primary().getCouncil();
  }

  public void addToCouncil(SeatedAdvisor seated) {
    primary().addToCouncil(seated);
  }

  public void removeFromCouncil(String advisorId) {
    primary().removeFromCouncil(advisorId);
  }

  public SeatedAdvisor findSeatedAdvisor(String advisorId) {
    return primary().findSeatedAdvisor(advisorId);
  }

  @JsonProperty("councilOfferChoices")
  public List<Advisor> getCouncilOfferChoices() {
    return primary().getCouncilOfferChoices();
  }

  public void setCouncilOfferChoices(List<Advisor> councilOfferChoices) {
    primary().setCouncilOfferChoices(councilOfferChoices);
  }

  @JsonProperty("currentProposal")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public CouncilProposal getCurrentProposal() {
    return primary().getCurrentProposal();
  }

  public void setCurrentProposal(CouncilProposal currentProposal) {
    primary().setCurrentProposal(currentProposal);
  }

  @JsonProperty("permanentFreeRerolls")
  public int getPermanentFreeRerolls() {
    return primary().getPermanentFreeRerolls();
  }

  public void setPermanentFreeRerolls(int permanentFreeRerolls) {
    primary().setPermanentFreeRerolls(permanentFreeRerolls);
  }

  public void incrementPermanentFreeRerolls(int delta) {
    primary().incrementPermanentFreeRerolls(delta);
  }

  @JsonProperty("acceleratedGreatWorkNames")
  public Set<String> getAcceleratedGreatWorkNames() {
    return primary().getAcceleratedGreatWorkNames();
  }

  public void markGreatWorkAccelerated(String greatWorkName) {
    primary().markGreatWorkAccelerated(greatWorkName);
  }

  public void apply(Effect effect) {
    primary().apply(effect);
  }

  public void consumeFood(double rate) {
    primary().consumeFood(rate);
  }

  public void addProduction(double value) {
    primary().addProduction(value);
  }

  public void addGold(double value) {
    primary().addGold(value);
  }

  public void addPopulationCap(double value) {
    primary().addPopulationCap(value);
  }

  public List<Card> getCardsOnBoardAfter(final Card card) {
    return primary().getCardsOnBoardAfter(season(), card);
  }

  // ----- Builder -----

  /**
   * Builds a single-player match. Mirrors the old flat {@code GameState.Builder} fluent API: the
   * clock setters ({@link #season}, {@link #phase}, {@link #year}) live here, and every per-player
   * setter forwards to an embedded {@link PlayerState.Builder}. This keeps existing call sites
   * (both production and tests) compiling without change.
   */
  public static final class Builder {
    private final PlayerState.Builder player = PlayerState.builder();
    private Season season = Season.SPRING;
    private Phase phase = Phase.ADD;
    private int year = 1;

    // Set directly by singlePlayer(...).
    private List<PlayerState> players;
    private TimeState time;

    private Builder() {}

    public Builder table(Season season, List<Card> cards) {
      player.table(season, cards);
      return this;
    }

    public Builder discardPile(Season season, List<Card> cards) {
      player.discardPile(season, cards);
      return this;
    }

    public Builder drawPile(Season season, List<Card> cards) {
      player.drawPile(season, cards);
      return this;
    }

    public Builder hand(List<Card> cards) {
      player.hand(cards);
      return this;
    }

    public Builder population(double population) {
      player.population(population);
      return this;
    }

    public Builder populationCap(double populationCap) {
      player.populationCap(populationCap);
      return this;
    }

    public Builder production(double production) {
      player.production(production);
      return this;
    }

    public Builder food(double food) {
      player.food(food);
      return this;
    }

    public Builder season(Season season) {
      this.season = season;
      return this;
    }

    public Builder phase(Phase phase) {
      this.phase = phase;
      return this;
    }

    public Builder year(int year) {
      this.year = year;
      return this;
    }

    public Builder addChoices(List<Card> addChoices) {
      player.addChoices(addChoices);
      return this;
    }

    public Builder rerollCount(int rerollCount) {
      player.rerollCount(rerollCount);
      return this;
    }

    public Builder resolutionLog(List<ResolutionStep> resolutionLog) {
      player.resolutionLog(resolutionLog);
      return this;
    }

    public Builder gold(double gold) {
      player.gold(gold);
      return this;
    }

    public Builder strength(double strength) {
      player.strength(strength);
      return this;
    }

    public Builder goldUnlocked(boolean goldUnlocked) {
      player.goldUnlocked(goldUnlocked);
      return this;
    }

    public Builder strengthUnlocked(boolean strengthUnlocked) {
      player.strengthUnlocked(strengthUnlocked);
      return this;
    }

    public Builder unlockedResources(Set<ResourceTarget> unlockedResources) {
      player.unlockedResources(unlockedResources);
      return this;
    }

    public Builder council(List<SeatedAdvisor> council) {
      player.council(council);
      return this;
    }

    public Builder councilOfferChoices(List<Advisor> councilOfferChoices) {
      player.councilOfferChoices(councilOfferChoices);
      return this;
    }

    public Builder currentProposal(CouncilProposal currentProposal) {
      player.currentProposal(currentProposal);
      return this;
    }

    public Builder permanentFreeRerolls(int permanentFreeRerolls) {
      player.permanentFreeRerolls(permanentFreeRerolls);
      return this;
    }

    public Builder acceleratedGreatWorkNames(Set<String> acceleratedGreatWorkNames) {
      player.acceleratedGreatWorkNames(acceleratedGreatWorkNames);
      return this;
    }

    public GameState build() {
      // singlePlayer(...) pre-populates players/time directly.
      if (players != null && time != null) {
        return new GameState(this);
      }
      if (season == null) {
        throw new IllegalStateException("missing current season");
      }
      if (phase == null) {
        throw new IllegalStateException("missing current phase");
      }
      if (year < 0) {
        throw new IllegalStateException("year cannot be negative");
      }
      this.players = new ArrayList<>(List.of(player.build()));
      this.time = new TimeState(season, phase, year);
      return new GameState(this);
    }
  }
}
