package com.codenzasoft.games.kingshand.strategies;

import com.codenzasoft.games.kingshand.Card;
import com.codenzasoft.games.kingshand.CardStaffing;
import com.codenzasoft.games.kingshand.Effect;
import com.codenzasoft.games.kingshand.FrequencyType;
import com.codenzasoft.games.kingshand.PlayerState;
import com.codenzasoft.games.kingshand.ResourceState;
import com.codenzasoft.games.kingshand.ResourceTarget;
import com.codenzasoft.games.kingshand.Season;
import java.util.List;

/**
 * Rules-faithful forward projection of a kingdom's food/production, used by {@link
 * KhStateEvaluator} and the draft evaluator. Every number here is derived from the same primitives
 * the engine uses at resolution time — worker staffing ({@link CardStaffing}), per-tier effects
 * ({@link Card#getTierEffects}), the Winter ×2 food rate, and {@link ResourceState#consumeFood} for
 * the starvation/growth step — so the projection can never quietly disagree with real gameplay.
 *
 * <p>The projection is deliberately <em>conservative</em>: it counts only {@link
 * FrequencyType#RECURRING} generation for future seasons (single-use cards will have been discarded
 * and future draws are hidden), so it under-estimates rather than over-estimates food. That bias is
 * the safe one for a survival-first bot.
 */
public final class KhStateProjection {

  private KhStateProjection() {}

  /** Food consumed per population per upkeep: 2 in Winter, 1 otherwise (see the engine). */
  public static int foodRate(final Season season) {
    return season == Season.WINTER ? 2 : 1;
  }

  /** Food consumed at {@code season}'s upkeep for {@code population}: {@code floor(pop × rate)}. */
  public static double upkeepConsumption(final double population, final Season season) {
    return Math.floor(population * foodRate(season));
  }

  /**
   * Staffing-adjusted sum of the direct {@code target} effect modifiers produced by cards of {@code
   * frequency} on {@code season}'s table, evaluated at the kingdom's current population. Mirrors
   * the engine's resolution: workers fill left-to-right, each card fires at its {@link
   * com.codenzasoft.games.kingshand.StaffingTier}, and Great Works only contribute when fully
   * staffed. Passing {@code frequency == null} counts every card regardless of frequency.
   */
  public static double staffedResourceOutput(
      final PlayerState player,
      final Season season,
      final ResourceTarget target,
      final FrequencyType frequency) {
    final List<Card> table = player.getTable(season);
    final List<CardStaffing> staffing = CardStaffing.computeStaffing(table, player.getResources());
    double sum = 0.0;
    for (int i = 0; i < table.size(); i++) {
      final Card card = table.get(i);
      if (frequency != null && card.frequencyType() != frequency) {
        continue;
      }
      for (final Effect e : card.getTierEffects(staffing.get(i).tier())) {
        if (e.target() == target) {
          sum += e.modifier();
        }
      }
    }
    return sum;
  }

  /** Recurring food this kingdom generates on {@code season}'s table, staffing-adjusted. */
  public static double recurringFood(final PlayerState player, final Season season) {
    return staffedResourceOutput(player, season, ResourceTarget.FOOD, FrequencyType.RECURRING);
  }

  /** Recurring production this kingdom generates on {@code season}'s table, staffing-adjusted. */
  public static double recurringProduction(final PlayerState player, final Season season) {
    return staffedResourceOutput(
        player, season, ResourceTarget.PRODUCTION, FrequencyType.RECURRING);
  }

  /**
   * Food runway in upkeeps: how many upkeep cycles the current reserve covers once recurring food
   * income is netted against consumption at {@code nextUpkeepSeason}. A kingdom whose recurring
   * food already meets or beats its upkeep is sustainable, so its runway is reported as {@code
   * sustainableCap} (a large but finite number) rather than infinity.
   */
  public static double foodRunwayUpkeeps(
      final PlayerState player, final Season nextUpkeepSeason, final double sustainableCap) {
    final double pop = player.getResources().population();
    final double consumption = upkeepConsumption(pop, nextUpkeepSeason);
    final double income = recurringFood(player, nextUpkeepSeason);
    final double net = consumption - income; // food burned from reserve each upkeep
    if (net <= 0.0) {
      return sustainableCap;
    }
    return player.getResources().food() / net;
  }

  /**
   * Outcome of walking the next {@code horizon} upkeeps forward with only recurring income.
   *
   * @param seasonsSurvived how many upkeeps were survived before collapse (equals the horizon when
   *     the kingdom never starves) — a later collapse is strictly better than an earlier one
   */
  public record FoodOutlook(
      double minFood, double endFood, double endPopulation, boolean starves, int seasonsSurvived) {}

  /**
   * Projects the next {@code horizon} upkeeps starting at {@code firstUpkeepSeason}, applying each
   * season's recurring food income and then the real {@link ResourceState#consumeFood} step (which
   * carries the exact starvation and surplus-growth rules). Reports the minimum food seen, the
   * ending food/population, and whether population would collapse below 1 within the horizon.
   *
   * <p>Future placements and draws are unknown, so only recurring income is credited — the
   * projection answers "if I placed nothing more, do I starve within the horizon?".
   */
  public static FoodOutlook projectFood(
      final PlayerState player, final Season firstUpkeepSeason, final int horizon) {
    ResourceState rs = player.getResources();
    Season season = firstUpkeepSeason;
    double minFood = rs.food();
    boolean starves = false;
    int seasonsSurvived = horizon;
    for (int i = 0; i < horizon; i++) {
      final double income = recurringFood(player, season);
      rs = rs.withFood(Math.max(0.0, rs.food() + income));
      rs = rs.consumeFood(foodRate(season));
      minFood = Math.min(minFood, rs.food());
      if (rs.population() < 1.0) {
        starves = true;
        seasonsSurvived = i;
        break;
      }
      season = season.next();
    }
    return new FoodOutlook(minFood, rs.food(), rs.population(), starves, seasonsSurvived);
  }

  /**
   * Classifies survival from food runway, recurring food sustainability, and a production floor.
   *
   * @param nextUpkeepSeason the season of the next food upkeep this state faces
   */
  public static SurvivalCategory classify(
      final PlayerState player, final Season nextUpkeepSeason, final KhBotConfig config) {
    final double pop = player.getResources().population();
    final double consumption = upkeepConsumption(pop, nextUpkeepSeason);
    final double reserveUpkeeps =
        consumption <= 0 ? config.strongFoodUpkeeps : player.getResources().food() / consumption;
    final double runway =
        foodRunwayUpkeeps(player, nextUpkeepSeason, config.strongFoodUpkeeps * 2.0);
    final boolean sustainable = recurringFood(player, nextUpkeepSeason) >= consumption;
    final boolean productionOk = player.getResources().production() >= config.productionFloor;

    if (reserveUpkeeps <= config.criticalFoodUpkeeps && !sustainable) {
      return SurvivalCategory.CRITICAL;
    }
    if (runway < config.unsafeFoodUpkeeps || !productionOk) {
      return SurvivalCategory.UNSAFE;
    }
    if (sustainable && runway >= config.strongFoodUpkeeps && productionOk) {
      return SurvivalCategory.STRONG;
    }
    return SurvivalCategory.STABLE;
  }
}
