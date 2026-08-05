package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Immutable match-level state for a King's Hand game: the shared clock ({@link TimeState}), the
 * list of per-player kingdoms ({@link PlayerState}) addressed by seat, the readiness barrier's set
 * of submitted seats, and the held-resolution flag.
 *
 * <p>{@code GameState} owns only what is shared or cross-cutting across the match. Per-player
 * operations live on {@link PlayerState} and are reached via {@link #getPlayer(int)}. All mutation
 * is expressed as {@code with*} / delegate methods returning a new instance.
 *
 * <p>For a single-player game {@code players} has exactly one element (seat 0). Read accessors are
 * exposed as {@code getX()} methods to keep existing call sites unchanged.
 */
public record GameState(
    List<PlayerState> players,
    TimeState time,
    Set<Integer> submittedSeats,
    boolean resolutionPending) {

  /** Compact constructor — normalise nulls and defensively copy collections. */
  public GameState {
    players = players == null ? List.of() : List.copyOf(players);
    submittedSeats = submittedSeats == null ? Set.of() : Set.copyOf(submittedSeats);
  }

  // ===== Static factories =====

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
   * Creates a fresh {@code count}-player match: {@code count} independent starting kingdoms sharing
   * one clock (Spring, year 1, ADD phase).
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
   * Wraps already-built kingdoms and a shared {@link TimeState} into a match.
   *
   * @throws IllegalArgumentException if {@code players} is null or empty
   */
  public static GameState multiPlayer(List<PlayerState> players, TimeState time) {
    if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("a match needs at least one player");
    }
    return new GameState(players, time, Set.of(), false);
  }

  // ===== Match structure =====

  /** All player kingdoms in this match, ordered by seat. Single-player games have one element. */
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

  /** This match with the kingdom at {@code seat} replaced by {@code player}. */
  public GameState withPlayer(int seat, PlayerState player) {
    List<PlayerState> next = new ArrayList<>(players);
    next.set(seat, player);
    return new GameState(next, time, submittedSeats, resolutionPending);
  }

  // ===== Shared clock =====

  public TimeState getTime() {
    return time;
  }

  public GameState withTime(TimeState time) {
    return new GameState(players, time, submittedSeats, resolutionPending);
  }

  /**
   * Advances the shared clock. The Council opens after Winter's PLAY when <em>either</em> kingdom
   * has unlocked GOLD, so a player with a gold economy always gets their council turn.
   */
  public GameState advanceTime() {
    boolean anyGoldUnlocked =
        players.stream().anyMatch(p -> p.getResources().isUnlocked(ResourceTarget.GOLD));
    return withTime(time.advance(anyGoldUnlocked));
  }

  // ===== Readiness barrier (per-phase submit tracking) =====

  /** This match with {@code seat} marked as having completed the current phase. */
  public GameState markSubmitted(int seat) {
    Set<Integer> next = new java.util.HashSet<>(submittedSeats);
    next.add(seat);
    return new GameState(players, time, next, resolutionPending);
  }

  /** {@code true} if {@code seat} has already submitted the current phase. */
  public boolean isSubmitted(int seat) {
    return submittedSeats.contains(seat);
  }

  /**
   * {@code true} once every live (not knocked-out / forfeited) seat has submitted the current
   * phase.
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

  /** This match with all submit marks cleared. Called whenever the phase changes. */
  public GameState clearSubmitted() {
    return new GameState(players, time, Set.of(), resolutionPending);
  }

  /** Seats that have submitted the current phase, sorted for a stable wire order. */
  public List<Integer> getSubmittedSeats() {
    return submittedSeats.stream().sorted().toList();
  }

  /**
   * {@code true} while the resolved season's clock is being held until every seat clicks Continue
   * on the multiplayer resolution screen.
   */
  public boolean isResolutionPending() {
    return resolutionPending;
  }

  public GameState withResolutionPending(final boolean resolutionPending) {
    return new GameState(players, time, submittedSeats, resolutionPending);
  }

  // ===== Match outcome (cross-player) =====

  /** {@code true} if {@code seat}'s population has collapsed (knocked out / forfeit). */
  public boolean isPlayerOut(int seat) {
    return getPlayer(seat).isGameOver();
  }

  /** {@code true} if {@code seat} has completed all of their win-condition Great Works. */
  public boolean isPlayerVictorious(int seat) {
    return getPlayer(seat).isVictoryComplete();
  }

  /** {@code true} once the match is decided: some seat has won, or a seat has been knocked out. */
  public boolean isMatchOver() {
    for (int seat = 0; seat < players.size(); seat++) {
      if (isPlayerVictorious(seat) || isPlayerOut(seat)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Seat of the winner, or {@code -1} if the match is not yet decided or ended in a draw. Great
   * Works wins outright for a surviving kingdom; otherwise the sole survivor wins by conquest. A
   * mutual knockout is a draw.
   */
  public int winnerSeat() {
    for (int seat = 0; seat < players.size(); seat++) {
      if (isPlayerVictorious(seat) && !isPlayerOut(seat)) {
        return seat;
      }
    }
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

  /** How the match was won: {@code "GREAT_WORKS"}, {@code "CONQUEST"}, or {@code "NONE"}. */
  public String victoryType() {
    final int winner = winnerSeat();
    if (winner < 0) {
      return "NONE";
    }
    return isPlayerVictorious(winner) ? "GREAT_WORKS" : "CONQUEST";
  }

  // ===== Builder =====

  /**
   * Builds a single-player match. Mirrors the old flat fluent API: clock setters live here and
   * every per-player setter forwards to an embedded {@link PlayerState.Builder}. Multi-kingdom
   * matches are assembled via {@link #newMultiplayer(int)} / {@link #multiPlayer(List, TimeState)}.
   */
  public static final class Builder {
    private final PlayerState.Builder player = PlayerState.builder();
    private Season season = Season.SPRING;
    private Phase phase = Phase.ADD;
    private int year = 1;

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
      if (season == null) {
        throw new IllegalStateException("missing current season");
      }
      if (phase == null) {
        throw new IllegalStateException("missing current phase");
      }
      if (year < 0) {
        throw new IllegalStateException("year cannot be negative");
      }
      List<PlayerState> players = new ArrayList<>(List.of(player.build()));
      return new GameState(players, new TimeState(season, phase, year), Set.of(), false);
    }
  }
}
