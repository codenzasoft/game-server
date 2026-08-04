package com.codenzasoft.games.kingshand.strategies;

import com.codenzasoft.games.kingshand.Card;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.Phase;
import com.codenzasoft.games.kingshand.PlayerState;
import com.codenzasoft.games.kingshand.Season;
import com.codenzasoft.games.kingshand.strategies.KhStateProjection.FoodOutlook;

/**
 * The single place scoring rules live. Turns a {@link GameState} (viewed from one seat) into a
 * scalar the search maximises, with survival acting as a near-hard constraint rather than one more
 * equally-weighted bonus.
 *
 * <p>Evaluation order mirrors the brief:
 *
 * <ol>
 *   <li>Terminal win/loss — dominates everything.
 *   <li>Guaranteed starvation within the projection horizon — an enormous penalty.
 *   <li>Economic sustainability (food reserve, runway, recurring food/production, headroom).
 *   <li>Board and card quality (playable vs dead cards, Great Work progress).
 *   <li>Opponent pressure — only once the bot's own position is at least STABLE.
 * </ol>
 *
 * <p>Every weight comes from {@link KhBotConfig}, and {@link #evaluate} returns a full {@link
 * Score} breakdown so the decision log can explain why one action beat another during balancing.
 */
public final class KhStateEvaluator {

  private final KhBotConfig config;

  public KhStateEvaluator(final KhBotConfig config) {
    this.config = config;
  }

  /** A scored state, decomposed so the debug log can attribute the total. */
  public record Score(
      double total,
      SurvivalCategory category,
      double survival,
      double economy,
      double offense,
      double projectedMinFood,
      double projectedProduction,
      double foodRunway,
      boolean terminal,
      String terminalLabel) {}

  public KhBotConfig config() {
    return config;
  }

  /** Scores {@code state} from {@code seat}'s point of view. Higher is better for that seat. */
  public Score evaluate(final GameState state, final int seat) {
    final PlayerState me = state.getPlayer(seat);

    // 1. Terminal states dominate.
    if (me.isGameOver()) {
      return terminal(-config.terminalMagnitude, "DEFEAT (knocked out)");
    }
    if (me.isVictoryComplete()) {
      return terminal(config.terminalMagnitude, "VICTORY (great works)");
    }
    if (state.isMatchOver()) {
      final int winner = state.winnerSeat();
      if (winner == seat) {
        return terminal(config.terminalMagnitude, "VICTORY (conquest)");
      }
      if (winner >= 0) {
        return terminal(-config.terminalMagnitude, "DEFEAT (opponent won)");
      }
      return terminal(-config.terminalMagnitude * 0.5, "DRAW (mutual knockout)");
    }

    final Season nextUpkeep = nextUpkeepSeason(state);
    final SurvivalCategory category = KhStateProjection.classify(me, nextUpkeep, config);
    final FoodOutlook outlook =
        KhStateProjection.projectFood(me, nextUpkeep, config.projectionHorizonSeasons);
    final double runway =
        KhStateProjection.foodRunwayUpkeeps(me, nextUpkeep, config.strongFoodUpkeeps * 2.0);

    final double survival = survivalScore(me, category, outlook, runway);
    final double economy = economyScore(state, seat, nextUpkeep);
    final double offense = offenseScore(state, seat, category);

    return new Score(
        survival + economy + offense,
        category,
        survival,
        economy,
        offense,
        outlook.minFood(),
        me.getResources().production(),
        runway,
        false,
        null);
  }

  // ----- 2. Survival -----

  private double survivalScore(
      final PlayerState me,
      final SurvivalCategory category,
      final FoodOutlook outlook,
      final double runway) {
    double score = 0.0;
    // Runway is the backbone: unsupported growth raises upkeep, which lowers runway, which lowers
    // this term — so growth pays for itself only when food generation can carry it.
    score += config.foodRunwayWeight * Math.min(runway, config.strongFoodUpkeeps * 2.0);
    // Value the SUSTAINABLE population floor, not a spike: a line that inflates population this
    // turn
    // but starves it back down within the horizon is judged by where it ends up, so a doomed growth
    // play gets no credit for the workers it briefly adds.
    final double sustainablePop =
        Math.floor(Math.min(me.getResources().population(), outlook.endPopulation()));
    score += config.populationWeight * sustainablePop;
    if (outlook.starves()) {
      // Almost a loss — and starving sooner is strictly worse than starving later, so a plan that
      // buys more seasons before collapse is preferred when collapse is otherwise unavoidable.
      final double soonness = config.projectionHorizonSeasons - outlook.seasonsSurvived() + 1;
      score -= config.starvationPenalty * soonness;
    } else {
      // Surviving the whole horizon is a real, bankable good.
      score += config.foodRunwayWeight;
    }
    // Category acts as a coarse floor/ceiling nudge.
    score += categoryBias(category);
    return score;
  }

  private double categoryBias(final SurvivalCategory category) {
    return switch (category) {
      case CRITICAL -> -config.starvationPenalty * 0.25;
      case UNSAFE -> -config.foodRunwayWeight;
      case STABLE -> 0.0;
      case STRONG -> config.foodRunwayWeight;
    };
  }

  // ----- 3 & 4. Economy and board quality -----

  private double economyScore(final GameState state, final int seat, final Season nextUpkeep) {
    final PlayerState me = state.getPlayer(seat);
    double score = 0.0;
    score += config.foodReserveWeight * me.getResources().food();
    score += config.productionWeight * me.getResources().production();
    score += config.recurringFoodWeight * sumRecurringFoodAllSeasons(me);
    score += config.recurringProductionWeight * sumRecurringProductionAllSeasons(me);
    // Headroom below the population cap is future growth potential, but only mildly valued.
    score +=
        config.populationHeadroomWeight
            * Math.max(0.0, me.getResources().populationCap() - me.getResources().population());
    // Card quality: reward a hand you can actually deploy, penalise dead/unaffordable cards.
    final Season season = state.getTime().season();
    int playable = 0;
    int dead = 0;
    for (final Card c : me.getHand()) {
      if (isPlayableNow(me, season, c)) {
        playable++;
      } else {
        dead++;
      }
    }
    score += config.playableCardWeight * playable;
    score -= config.deadCardPenalty * dead;
    score += config.greatWorkProgressWeight * greatWorkProgress(me);
    return score;
  }

  private double sumRecurringFoodAllSeasons(final PlayerState me) {
    double sum = 0.0;
    for (final Season s : Season.values()) {
      sum += KhStateProjection.recurringFood(me, s);
    }
    return sum;
  }

  private double sumRecurringProductionAllSeasons(final PlayerState me) {
    double sum = 0.0;
    for (final Season s : Season.values()) {
      sum += KhStateProjection.recurringProduction(me, s);
    }
    return sum;
  }

  private boolean isPlayableNow(final PlayerState me, final Season season, final Card c) {
    final boolean affordable = me.getResources().production() >= c.productionRequirement();
    final boolean hasSlot =
        me.tableContains(season, c) || me.getTable(season).size() < 5; // MAX_TABLE_SIZE
    return affordable && hasSlot;
  }

  /**
   * Progress toward a Great-Works victory: completed victory monuments count for far more than ones
   * merely under construction, and the two economy-unlocking Great Works (TREASURY/BARRACKS) count
   * a little for the doors they open.
   */
  private double greatWorkProgress(final PlayerState me) {
    double progress = 0.0;
    for (final Season s : Season.values()) {
      for (final Card c : me.getTable(s)) {
        if (c.victory() && !c.isGreatWork()) {
          progress += 4.0; // a completed win-condition monument
        } else if (c.victory() && c.isGreatWork()) {
          progress += 1.0; // a win-condition monument still under construction
        } else if (c.isGreatWork()) {
          progress += 0.25; // TREASURY/BARRACKS in progress
        }
      }
    }
    return progress;
  }

  // ----- 5. Opponent pressure -----

  private double offenseScore(
      final GameState state, final int seat, final SurvivalCategory category) {
    // Don't spend anything reasoning about offense until our own house is in order.
    if (category == SurvivalCategory.CRITICAL || category == SurvivalCategory.UNSAFE) {
      return 0.0;
    }
    double score = 0.0;
    for (int other = 0; other < state.playerCount(); other++) {
      if (other == seat) {
        continue;
      }
      final PlayerState opp = state.getPlayer(other);
      if (opp.isGameOver()) {
        continue;
      }
      final Season oppNext = nextUpkeepSeason(state);
      final FoodOutlook oppOutlook =
          KhStateProjection.projectFood(opp, oppNext, config.projectionHorizonSeasons);
      // The lower the opponent's population and the closer they are to starving, the better.
      score +=
          config.opponentPressureWeight * (100.0 - Math.floor(opp.getResources().population()));
      if (oppOutlook.starves()) {
        score += config.opponentPressureWeight * 200.0;
      }
      // Weight offense up as our own position strengthens.
      if (category == SurvivalCategory.STRONG) {
        score *= 2.0;
      }
    }
    return score;
  }

  // ----- helpers -----

  /** The season whose food upkeep this state faces next. */
  private Season nextUpkeepSeason(final GameState state) {
    final Phase phase = state.getTime().phase();
    final Season season = state.getTime().season();
    final boolean alreadyResolvedThisSeason =
        state.isResolutionPending() || phase == Phase.COUNCIL_ADD || phase == Phase.COUNCIL_MEETING;
    return alreadyResolvedThisSeason ? season.next() : season;
  }

  private Score terminal(final double value, final String label) {
    return new Score(value, null, value, 0.0, 0.0, 0.0, 0.0, 0.0, true, label);
  }
}
