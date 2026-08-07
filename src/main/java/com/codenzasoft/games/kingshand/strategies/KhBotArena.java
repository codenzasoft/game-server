package com.codenzasoft.games.kingshand.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.Phase;
import com.codenzasoft.games.kingshand.PlayerState;
import com.codenzasoft.games.kingshand.ResolutionStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Headless self-play harness for balancing. Plays full King's Hand matches between two {@link
 * KhPlayerStrategy}s and collects per-game and aggregate metrics — win rate, years survived,
 * starvation frequency, and end-state economy — so a fixed set of seeded games can be run before
 * and after a card-value change to see what moved.
 *
 * <p>The match is <em>driven manually</em> (seats are built without registered strategies and each
 * phase is stepped explicitly) rather than relying on the engine's auto-{@code driveBots} loop.
 * That keeps the harness in control of a hard turn cap, so two survival-happy bots that would
 * otherwise stalemate forever can't hang the run.
 *
 * <p>Correlations reported here are descriptive only — a card appearing often in winning games does
 * <em>not</em> prove it causes wins; treat them as leads to investigate, not conclusions.
 */
public final class KhBotArena {

  /** Per-seat outcome of a single match. */
  public record SeatResult(
      int seat,
      boolean survived,
      boolean won,
      double finalFood,
      double finalProduction,
      double finalPopulation,
      int starvationSeasons) {}

  /** Outcome of a single match. */
  public record GameResult(
      long seed, int winnerSeat, String victoryType, int finalYear, List<SeatResult> seats) {}

  /** Aggregate metrics over many matches. */
  public record SeriesResult(
      int games,
      int seat0Wins,
      int seat1Wins,
      int draws,
      double avgFinalYear,
      double seat0StarvationRate,
      double seat1StarvationRate) {
    public double seat0WinRate() {
      return games == 0 ? 0.0 : (double) seat0Wins / games;
    }

    public double seat1WinRate() {
      return games == 0 ? 0.0 : (double) seat1Wins / games;
    }
  }

  private final Supplier<KhPlayerStrategy> seat0Factory;
  private final Supplier<KhPlayerStrategy> seat1Factory;
  private final int maxYears;

  public KhBotArena(
      final Supplier<KhPlayerStrategy> seat0Factory,
      final Supplier<KhPlayerStrategy> seat1Factory,
      final int maxYears) {
    this.seat0Factory = seat0Factory;
    this.seat1Factory = seat1Factory;
    this.maxYears = maxYears;
  }

  /** Plays one match with the given engine RNG seed and returns its metrics. */
  public GameResult playGame(final long seed) {
    final GameState state = GameState.newMultiplayer(2);
    final GameEngine engine =
        new GameEngine.Builder()
            .withId(UUID.randomUUID())
            .withState(state)
            .withPlayer(new Player(UUID.randomUUID(), "seat0"))
            .withPlayer(new Player(UUID.randomUUID(), "seat1"))
            .withRng(new Random(seed))
            .build();
    engine.initialize();

    final KhPlayerStrategy[] strat = {seat0Factory.get(), seat1Factory.get()};
    final int[] starvationSeasons = new int[2];

    int guard = 0;
    final int guardLimit = maxYears * 40 + 100;
    // State is immutable and the engine swaps in new instances, so re-read engine.getState() every
    // time rather than caching a (now-stale) reference.
    while (!engine.getState().isMatchOver()
        && engine.getState().getTime().year() <= maxYears
        && guard++ < guardLimit) {
      if (engine.getState().isResolutionPending()) {
        for (int seat = 0; seat < 2; seat++) {
          if (!engine.getState().isPlayerOut(seat) && !engine.getState().isSubmitted(seat)) {
            engine.setActiveSeat(seat);
            engine.continueResolution();
          }
        }
        continue;
      }
      final Phase phase = engine.getState().getTime().phase();
      for (int seat = 0; seat < 2; seat++) {
        if (engine.getState().isPlayerOut(seat)
            || engine.getState().isSubmitted(seat)
            || engine.getState().isMatchOver()) {
          continue;
        }
        engine.setActiveSeat(seat);
        try {
          strat[seat].act(engine, seat);
        } catch (final RuntimeException e) {
          forceComplete(
              engine, engine.getState().getTime().phase()); // never hang on a strategy bug
        }
      }
      // Count a starvation season whenever a PLAY resolution dropped population via the food step.
      if (phase == Phase.PLAY) {
        final GameState afterPlay = engine.getState();
        for (int seat = 0; seat < 2; seat++) {
          if (droppedPopulationToStarvation(afterPlay.getPlayer(seat))) {
            starvationSeasons[seat]++;
          }
        }
      }
    }

    final GameState finalState = engine.getState();
    final List<SeatResult> seats = new ArrayList<>(2);
    for (int seat = 0; seat < 2; seat++) {
      final PlayerState p = finalState.getPlayer(seat);
      seats.add(
          new SeatResult(
              seat,
              !p.isGameOver(),
              finalState.winnerSeat() == seat,
              p.getResources().food(),
              p.getResources().production(),
              p.getResources().population(),
              starvationSeasons[seat]));
    }
    return new GameResult(
        seed,
        finalState.winnerSeat(),
        finalState.victoryType(),
        finalState.getTime().year(),
        seats);
  }

  /** Plays {@code count} matches with seeds {@code baseSeed..baseSeed+count-1} and aggregates. */
  public SeriesResult playSeries(final long baseSeed, final int count) {
    int s0 = 0;
    int s1 = 0;
    int draws = 0;
    long yearSum = 0;
    int s0Starve = 0;
    int s1Starve = 0;
    for (int i = 0; i < count; i++) {
      final GameResult r = playGame(baseSeed + i);
      if (r.winnerSeat() == 0) {
        s0++;
      } else if (r.winnerSeat() == 1) {
        s1++;
      } else {
        draws++;
      }
      yearSum += r.finalYear();
      if (r.seats().get(0).starvationSeasons() > 0) {
        s0Starve++;
      }
      if (r.seats().get(1).starvationSeasons() > 0) {
        s1Starve++;
      }
    }
    return new SeriesResult(
        count,
        s0,
        s1,
        draws,
        count == 0 ? 0.0 : (double) yearSum / count,
        count == 0 ? 0.0 : (double) s0Starve / count,
        count == 0 ? 0.0 : (double) s1Starve / count);
  }

  /** Completes the active seat's phase with the default action after a strategy error. */
  private static void forceComplete(final GameEngine engine, final Phase phase) {
    try {
      switch (phase) {
        case ADD -> engine.skipAdd();
        case PLAY -> engine.endAndRun();
        case COUNCIL_ADD -> engine.skipCouncilAdd();
        case COUNCIL_MEETING -> engine.passCouncilProposal();
      }
    } catch (final RuntimeException ignored) {
      // already submitted or resolved — nothing more to do
    }
  }

  private static boolean droppedPopulationToStarvation(final PlayerState p) {
    for (final ResolutionStep step : p.getResolutionLog()) {
      // The tail food step has a null cardName; a negative population delta there is starvation.
      if (step.cardName() == null && step.delta().population() < 0.0) {
        return true;
      }
    }
    return false;
  }

  /** Convenience entry point for a quick local balance snapshot. */
  public static void main(final String[] args) {
    final int games = args.length > 0 ? Integer.parseInt(args[0]) : 50;
    final int maxYears = 40;

    final KhBotArena beamVsGreedy =
        new KhBotArena(
            () -> new KhBeamSearchStrategy(KhBotConfig.defaults()),
            KhHeuristicStrategy::new,
            maxYears);
    final SeriesResult r1 = beamVsGreedy.playSeries(1L, games);
    System.out.printf(
        "BeamSearch(seat0) vs Greedy(seat1) over %d games:%n"
            + "  seat0 winRate=%.2f seat1 winRate=%.2f draws=%d avgYear=%.1f%n"
            + "  starvationRate seat0=%.2f seat1=%.2f%n",
        games,
        r1.seat0WinRate(),
        r1.seat1WinRate(),
        r1.draws(),
        r1.avgFinalYear(),
        r1.seat0StarvationRate(),
        r1.seat1StarvationRate());

    final KhBotArena mirror =
        new KhBotArena(
            () -> new KhBeamSearchStrategy(KhBotConfig.defaults()),
            () -> new KhBeamSearchStrategy(KhBotConfig.defaults()),
            maxYears);
    final SeriesResult r2 = mirror.playSeries(1L, games);
    System.out.printf(
        "BeamSearch mirror over %d games: avgYear=%.1f starvationRate seat0=%.2f seat1=%.2f%n",
        games, r2.avgFinalYear(), r2.seat0StarvationRate(), r2.seat1StarvationRate());
  }
}
