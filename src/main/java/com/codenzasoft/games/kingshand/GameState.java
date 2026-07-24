package com.codenzasoft.games.kingshand;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Match-level state for a King's Hand game: the shared clock ({@link TimeState}) plus the list of
 * per-player kingdoms ({@link PlayerState}). This is the top-level object the engine mutates and
 * the client is sent.
 *
 * <p><b>Responsibilities.</b> {@code GameState} owns only what is shared or cross-cutting across
 * the match: the collection of kingdoms (addressed by <em>seat</em>), the single authoritative
 * season/phase/year clock, and the match outcome (who has won / been knocked out). It deliberately
 * does <em>not</em> re-expose per-player operations — those live on {@link PlayerState} and are
 * reached via {@link #getPlayer(int)}. The engine is seat-aware and operates on the relevant {@link
 * PlayerState} directly, so this class no longer delegates to a single "primary" kingdom.
 *
 * <p>For a single-player game {@code players} has exactly one element (seat 0).
 */
public class GameState {

  private final List<PlayerState> players;
  private TimeState time;

  /**
   * Seats that have completed ("submitted") the current phase. The shared clock must not advance
   * until every live seat has submitted — see {@code GameEngine}'s readiness barrier. Reset
   * whenever the phase changes.
   */
  private final Set<Integer> submittedSeats = new HashSet<>();

  /**
   * {@code true} while a multiplayer PLAY phase has resolved but the shared clock is being held on
   * the resolved season until every seat clicks "Continue" on the resolution screen. During this
   * window {@code submittedSeats} accumulates continue acknowledgements (a second barrier). Always
   * {@code false} in single-player, where the clock advances immediately after resolution.
   */
  private boolean resolutionPending;

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

  /** Number of seats (kingdoms) in this match. */
  public int playerCount() {
    return players.size();
  }

  // ----- Shared clock -----

  @JsonProperty("time")
  public TimeState getTime() {
    return time;
  }

  /**
   * Advances the shared clock. The Council opens after Winter's PLAY when <em>either</em> kingdom
   * has unlocked GOLD (the two-player council rule), so a player with a gold economy always gets
   * their council turn even if their opponent has none.
   */
  public void advanceTime() {
    boolean anyGoldUnlocked =
        players.stream().anyMatch(p -> p.getResources().isUnlocked(ResourceTarget.GOLD));
    time = time.advance(anyGoldUnlocked);
  }

  // ----- Readiness barrier (per-phase submit tracking) -----

  /**
   * Marks {@code seat} as having completed the current phase. The seat may take no further phase
   * actions until the barrier releases (all live seats submitted) and the clock advances.
   */
  public void markSubmitted(int seat) {
    submittedSeats.add(seat);
  }

  /** {@code true} if {@code seat} has already submitted the current phase. */
  public boolean isSubmitted(int seat) {
    return submittedSeats.contains(seat);
  }

  /**
   * {@code true} once every live (not knocked-out / forfeited) seat has submitted the current phase
   * — the readiness barrier releases and the shared clock may advance exactly once.
   */
  public boolean allSubmitted() {
    for (int seat = 0; seat < players.size(); seat++) {
      if (isPlayerOut(seat)) {
        continue;
      }
      if (!submittedSeats.contains(seat)) {
        return false;
      }
    }
    return true;
  }

  /** Clears all submit marks. Called whenever the phase changes. */
  public void clearSubmitted() {
    submittedSeats.clear();
  }

  /**
   * Seats that have submitted the current phase, sorted for a stable wire order. The client uses
   * this to show "waiting for other player…" once its own seat has submitted but others haven't.
   */
  @JsonProperty("submittedSeats")
  public List<Integer> getSubmittedSeats() {
    return submittedSeats.stream().sorted().toList();
  }

  /**
   * {@code true} while the resolved season's clock is being held until every seat clicks Continue
   * on the multiplayer resolution screen. The client shows the dual resolution while this is set
   * and sends {@code /play/continue} when the player clicks Continue.
   */
  @JsonProperty("resolutionPending")
  public boolean isResolutionPending() {
    return resolutionPending;
  }

  public void setResolutionPending(final boolean resolutionPending) {
    this.resolutionPending = resolutionPending;
  }

  // ----- Match outcome (cross-player) -----

  /**
   * {@code true} if {@code seat}'s population has collapsed (knocked out / forfeit by starvation).
   */
  public boolean isPlayerOut(int seat) {
    return getPlayer(seat).isGameOver();
  }

  /** {@code true} if {@code seat} has completed all of their win-condition Great Works. */
  public boolean isPlayerVictorious(int seat) {
    return getPlayer(seat).isVictoryComplete();
  }

  /**
   * {@code true} once the match is decided: some seat has completed all Great Works, or a seat has
   * been knocked out. Callers use {@link #winnerSeat()} to find who (if anyone) won.
   */
  @JsonProperty("matchOver")
  public boolean isMatchOver() {
    for (int seat = 0; seat < players.size(); seat++) {
      if (isPlayerVictorious(seat) || isPlayerOut(seat)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Seat of the winner, or {@code -1} if the match is not yet decided or ended in a draw.
   *
   * <p>Two equal paths to victory:
   *
   * <ul>
   *   <li><b>Great Works</b> — completing all win-condition Great Works wins outright, <em>provided
   *       that kingdom is still alive</em>. A kingdom that finishes its monuments but is wiped out
   *       the same turn does not win from the grave; the win falls through to the conqueror.
   *   <li><b>Conquest</b> — the last kingdom still standing wins once every opponent has been
   *       knocked out (population collapsed by combat/sabotage/starvation, or forfeit).
   * </ul>
   *
   * <p>A mutual knockout (every kingdom out in the same resolution) is a draw, returning {@code
   * -1}.
   */
  @JsonProperty("winnerSeat")
  public int winnerSeat() {
    // Great Works victory — but only for a kingdom that survived to enjoy it.
    for (int seat = 0; seat < players.size(); seat++) {
      if (isPlayerVictorious(seat) && !isPlayerOut(seat)) {
        return seat;
      }
    }
    // Conquest victory — sole survivor once every rival is knocked out.
    int survivors = 0;
    int lastSurvivor = -1;
    for (int seat = 0; seat < players.size(); seat++) {
      if (!isPlayerOut(seat)) {
        survivors++;
        lastSurvivor = seat;
      }
    }
    return (players.size() > 1 && survivors == 1) ? lastSurvivor : -1;
  }

  /**
   * How the match was won by {@link #winnerSeat()}: {@code "GREAT_WORKS"}, {@code "CONQUEST"}, or
   * {@code "NONE"} (undecided or a draw). Surfaced to the client so the game-over screen can name
   * the victory path.
   */
  @JsonProperty("victoryType")
  public String victoryType() {
    final int winner = winnerSeat();
    if (winner < 0) {
      return "NONE";
    }
    return isPlayerVictorious(winner) ? "GREAT_WORKS" : "CONQUEST";
  }

  // ----- Builder -----

  /**
   * Builds a single-player match. Mirrors the old flat {@code GameState.Builder} fluent API: the
   * clock setters ({@link #season}, {@link #phase}, {@link #year}) live here, and every per-player
   * setter forwards to an embedded {@link PlayerState.Builder}. This keeps existing single-kingdom
   * call sites (production and tests) compiling without change; multi-kingdom matches are assembled
   * via {@link #newMultiplayer(int)} / {@link #multiPlayer(List, TimeState)}.
   */
  public static final class Builder {
    private final PlayerState.Builder player = PlayerState.builder();
    private Season season = Season.SPRING;
    private Phase phase = Phase.ADD;
    private int year = 1;

    // Set directly by multiPlayer(...).
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
      // multiPlayer(...) pre-populates players/time directly.
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
