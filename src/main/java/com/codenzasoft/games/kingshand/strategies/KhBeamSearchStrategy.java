package com.codenzasoft.games.kingshand.strategies;

import com.codenzasoft.games.kingshand.Advisor;
import com.codenzasoft.games.kingshand.Card;
import com.codenzasoft.games.kingshand.CardTarget;
import com.codenzasoft.games.kingshand.CouncilProposal;
import com.codenzasoft.games.kingshand.Effect;
import com.codenzasoft.games.kingshand.FrequencyType;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.OpponentResourceTarget;
import com.codenzasoft.games.kingshand.PlayerState;
import com.codenzasoft.games.kingshand.ProposalKind;
import com.codenzasoft.games.kingshand.ResourceTarget;
import com.codenzasoft.games.kingshand.Season;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A survival-aware King's Hand bot. Replaces the greedy {@link KhHeuristicStrategy} with:
 *
 * <ul>
 *   <li><b>PLAY</b> — a shallow beam search over legal play plans (which hand cards to place, an
 *       optional Great Work commission, and the resolved table order), each simulated on a deep
 *       copy through the real engine and scored by {@link KhStateEvaluator}. It reorders the table
 *       so engine-boosters fire before the producers they boost (fixing the classic "FERTILIZER
 *       last" waste) and passes — places nothing harmful — when every placement makes the position
 *       worse.
 *   <li><b>ADD</b> — a state-aware draft: each offered card is scored against the current survival
 *       bottleneck, so a food card is worth far more when starving and an offensive card matters
 *       only once the economy is safe. Genuinely dilutive cards are skipped.
 *   <li><b>COUNCIL</b> — a light heuristic (hire an affordable advisor when there is room, take
 *       upside-only Decrees, pass draining Reforms).
 * </ul>
 *
 * <p>All randomness flows through a single seeded {@link Random}, so a given match state produces
 * identical decisions every run — essential for reproducible balance tests. Set {@link
 * KhBotConfig#debug} (or raise this logger to DEBUG) to log a full scoring breakdown per decision.
 */
public class KhBeamSearchStrategy implements KhPlayerStrategy {

  private static final Logger log = LoggerFactory.getLogger(KhBeamSearchStrategy.class);

  /** Safety cap on hand-subset enumeration; the real hand is at most {@code MAX_HAND_SIZE} (3). */
  private static final int MAX_SUBSET_CARDS = 4;

  private final KhBotConfig config;
  private final KhStateEvaluator evaluator;
  private final Random rng;

  public KhBeamSearchStrategy() {
    this(KhBotConfig.defaults());
  }

  public KhBeamSearchStrategy(final KhBotConfig config) {
    this.config = config;
    this.evaluator = new KhStateEvaluator(config);
    this.rng = new Random(config.seed);
  }

  public KhBotConfig config() {
    return config;
  }

  @Override
  public void act(final GameEngine engine, final int seat) {
    final GameState state = engine.getState();
    switch (state.getTime().phase()) {
      case ADD -> actAdd(engine, state, seat);
      case PLAY -> actPlay(engine, state, seat);
      case COUNCIL_ADD -> actCouncilAdd(engine, seat);
      case COUNCIL_MEETING -> actCouncilMeeting(engine, seat);
    }
  }

  // =====================================================================================
  // PLAY — beam search over play plans
  // =====================================================================================

  /** One legal PLAY plan: cards to place, an optional Great Work to commission, a table order. */
  private record PlayPlan(List<Card> placements, String commission, OrderStrategy order) {
    String describe() {
      final List<String> names = new ArrayList<>();
      placements.forEach(c -> names.add(c.name()));
      final StringBuilder sb = new StringBuilder("place=" + names + " order=" + order);
      if (commission != null) {
        sb.append(" commission=").append(commission);
      }
      return sb.toString();
    }
  }

  private void actPlay(final GameEngine engine, final GameState state, final int seat) {
    final PlayerState me = state.getPlayer(seat);
    final Season season = state.getTime().season();

    final List<PlayPlan> plans = enumeratePlayPlans(engine, state, seat, me, season);

    PlayPlan best = null;
    KhStateEvaluator.Score bestScore = null;
    PlayPlan runnerUp = null;
    KhStateEvaluator.Score runnerUpScore = null;
    int simulated = 0;

    for (final PlayPlan plan : plans) {
      if (simulated >= config.maxSimulationsPerDecision) {
        break;
      }
      simulated++;
      final GameState resolved =
          KhSimulator.simulatePlay(
              state, seat, plan.placements(), plan.commission(), plan.order().chooser(), rng);
      final KhStateEvaluator.Score score = evaluator.evaluate(resolved, seat);
      if (bestScore == null || score.total() > bestScore.total()) {
        runnerUp = best;
        runnerUpScore = bestScore;
        best = plan;
        bestScore = score;
      } else if (runnerUpScore == null || score.total() > runnerUpScore.total()) {
        runnerUp = plan;
        runnerUpScore = score;
      }
    }

    if (best == null) {
      engine.endAndRun(); // no legal plan produced — just end the season
      return;
    }

    logPlayDecision(state, seat, best, bestScore, runnerUp, runnerUpScore, simulated);
    applyPlayPlan(engine, seat, season, best);
  }

  /**
   * Enumerates candidate PLAY plans: every subset of the hand (placed cheapest-first for
   * affordability), each combined with a small set of table orderings, plus commission variants for
   * any Great Work commissionable this season once the position is not CRITICAL. Bounded by {@link
   * KhBotConfig#maxSimulationsPerDecision}.
   */
  private List<PlayPlan> enumeratePlayPlans(
      final GameEngine engine,
      final GameState state,
      final int seat,
      final PlayerState me,
      final Season season) {
    final List<PlayPlan> plans = new ArrayList<>();
    final List<Card> hand = me.getHand();
    final int n = Math.min(hand.size(), MAX_SUBSET_CARDS);
    final List<OrderStrategy> orders = orderStrategies();

    for (int mask = 0; mask < (1 << n); mask++) {
      final List<Card> subset = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        if ((mask & (1 << i)) != 0) {
          subset.add(hand.get(i));
        }
      }
      subset.sort(Comparator.comparingDouble(Card::productionRequirement)); // afford cheapest first
      for (final OrderStrategy order : orders) {
        plans.add(new PlayPlan(subset, null, order));
      }
    }

    // Commission variants — only worth simulating once we're not fighting for survival.
    final SurvivalCategory category = KhStateProjection.classify(me, season, config);
    if (category != SurvivalCategory.CRITICAL) {
      for (final Card gw : engine.availableGreatWorks(season)) {
        if (!me.areRequiredResourcesUnlocked(gw)) {
          continue;
        }
        if (me.getResources().production() < gw.productionRequirement()) {
          continue;
        }
        plans.add(new PlayPlan(List.of(), gw.name(), OrderStrategy.SURVIVAL_FIRST));
      }
    }
    return plans;
  }

  /** Replays the chosen plan on the live engine (our seat only) and ends the season. */
  private void applyPlayPlan(
      final GameEngine engine, final int seat, final Season season, final PlayPlan plan) {
    for (final Card card : plan.placements()) {
      try {
        engine.placeCard(card);
      } catch (final RuntimeException e) {
        // Skip a card validation rejects; keep the rest of the plan.
      }
    }
    if (plan.commission() != null) {
      try {
        engine.commissionGreatWork(plan.commission());
      } catch (final RuntimeException e) {
        // Commission no longer legal; proceed without it.
      }
    }
    final List<String> order =
        plan.order().chooser().apply(engine.getState().getPlayer(seat).getTable(season));
    if (order != null
        && order.size() == engine.getState().getPlayer(seat).getTable(season).size()) {
      try {
        engine.reorderTable(order);
      } catch (final RuntimeException e) {
        // Leave the natural order.
      }
    }
    engine.endAndRun();
  }

  // ----- table ordering strategies -----

  /**
   * How the resolved table is ordered. Because the engine resolves left-to-right and staffs workers
   * left-to-right, order decides both which engine-boosters fire before their targets and which
   * cards get staffed first when the workforce is short.
   */
  private enum OrderStrategy {
    /** Leave the table as placed. */
    NATURAL(table -> null),
    /**
     * Engine-boosters (pure {@link CardTarget} cards) first, so they boost the producers to their
     * right — this is the fix for FERTILIZER/WINDMILL/TEXTILES landing last and doing nothing.
     */
    BOOSTERS_FIRST(KhBeamSearchStrategy::orderBoostersFirst),
    /**
     * Boosters first, then producers ordered so the cheapest-to-staff fire first under a shortage.
     */
    SURVIVAL_FIRST(KhBeamSearchStrategy::orderSurvivalFirst);

    private final Function<List<Card>, List<String>> chooser;

    OrderStrategy(final Function<List<Card>, List<String>> chooser) {
      this.chooser = chooser;
    }

    Function<List<Card>, List<String>> chooser() {
      return chooser;
    }
  }

  private List<OrderStrategy> orderStrategies() {
    final List<OrderStrategy> all =
        List.of(OrderStrategy.NATURAL, OrderStrategy.BOOSTERS_FIRST, OrderStrategy.SURVIVAL_FIRST);
    return all.subList(0, Math.min(config.tableOrderingsPerPlan, all.size()));
  }

  private static boolean isBooster(final Card c) {
    return c.isEngineBoost();
  }

  private static List<String> orderBoostersFirst(final List<Card> table) {
    final List<Card> boosters = new ArrayList<>();
    final List<Card> rest = new ArrayList<>();
    for (final Card c : table) {
      (isBooster(c) ? boosters : rest).add(c);
    }
    final List<String> order = new ArrayList<>();
    boosters.forEach(c -> order.add(c.name()));
    rest.forEach(c -> order.add(c.name()));
    return order;
  }

  private static List<String> orderSurvivalFirst(final List<Card> table) {
    final List<Card> boosters = new ArrayList<>();
    final List<Card> rest = new ArrayList<>();
    for (final Card c : table) {
      (isBooster(c) ? boosters : rest).add(c);
    }
    rest.sort(Comparator.comparingDouble(Card::staffRequirement)); // cheapest to fully staff first
    final List<String> order = new ArrayList<>();
    boosters.forEach(c -> order.add(c.name()));
    rest.forEach(c -> order.add(c.name()));
    return order;
  }

  // =====================================================================================
  // ADD — state-aware drafting
  // =====================================================================================

  private void actAdd(final GameEngine engine, final GameState state, final int seat) {
    final PlayerState me = state.getPlayer(seat);
    final Season season = state.getTime().season();
    final List<Card> choices = me.getAddChoices();
    if (choices.isEmpty()) {
      engine.skipAdd();
      return;
    }

    final SurvivalCategory category = KhStateProjection.classify(me, season, config);

    Card best = null;
    double bestScore = skipBaseline(category); // skipping is the baseline; a pick must beat it
    Card runnerUp = null;
    double runnerUpScore = Double.NEGATIVE_INFINITY;

    for (final Card c : choices) {
      final double s = draftScore(me, season, c, category);
      if (s > bestScore) {
        runnerUp = best;
        runnerUpScore = bestScore;
        best = c;
        bestScore = s;
      } else if (s > runnerUpScore) {
        runnerUp = c;
        runnerUpScore = s;
      }
    }

    if (config.debug || log.isDebugEnabled()) {
      log.debug(
          "[KHBOT seat {}] ADD {} {} | category={} pick={} score={} runnerUp={} ({})",
          seat,
          season,
          me.getResources(),
          category,
          best == null ? "SKIP" : best.name(),
          fmt(bestScore),
          runnerUp == null ? "-" : runnerUp.name(),
          fmt(runnerUpScore));
    }

    if (best == null) {
      engine.skipAdd();
    } else {
      engine.pickCard(best);
    }
  }

  /** Skipping is worth a small positive baseline so the bot declines genuinely dilutive cards. */
  private double skipBaseline(final SurvivalCategory category) {
    return switch (category) {
      case CRITICAL, UNSAFE -> 5.0; // starving: only take a card that actually helps
      case STABLE -> 2.0;
      case STRONG -> 0.0; // healthy: almost any card is worth banking
    };
  }

  /**
   * Scores an offered draft card against the current position. The value of a card is not fixed: it
   * depends on the survival bottleneck, whether the bot can afford to play it, whether it addresses
   * a weakness, and whether it adds upkeep the bot cannot yet support.
   */
  private double draftScore(
      final PlayerState me, final Season season, final Card card, final SurvivalCategory category) {
    final double recurringFood = directOutput(card, ResourceTarget.FOOD, FrequencyType.RECURRING);
    final double singleFood = directOutput(card, ResourceTarget.FOOD, FrequencyType.SINGLE);
    final double recurringProd =
        directOutput(card, ResourceTarget.PRODUCTION, FrequencyType.RECURRING);
    final double singleProd = directOutput(card, ResourceTarget.PRODUCTION, FrequencyType.SINGLE);
    final double popAdd = directOutput(card, ResourceTarget.POPULATION, null);
    final double capAdd = directOutput(card, ResourceTarget.POPULATION_CAP, null);
    final boolean booster = isBooster(card);
    final boolean offensive = isOffensive(card);
    final boolean greatWork = card.isGreatWork();
    final double production = me.getResources().production();

    // ----- The card's play value if we could deploy it (before affordability discount) -----
    double playValue = 0.0;

    final double foodMultiplier =
        switch (category) {
          case CRITICAL -> 6.0;
          case UNSAFE -> 3.0;
          case STABLE -> 1.0;
          case STRONG -> 0.5;
        };
    playValue += foodMultiplier * (recurringFood * 1.5 + singleFood * 0.6);

    final double prodMultiplier = production < config.productionFloor ? 3.0 : 1.0;
    playValue += prodMultiplier * (recurringProd * 0.8 + singleProd * 0.3);

    if (booster) {
      playValue +=
          (category == SurvivalCategory.STABLE || category == SurvivalCategory.STRONG) ? 30.0 : 8.0;
    }
    if (offensive) {
      playValue +=
          switch (category) {
            case CRITICAL, UNSAFE -> -20.0;
            case STABLE -> 5.0;
            case STRONG -> 25.0;
          };
    }
    if (greatWork) {
      playValue += (category == SurvivalCategory.STRONG) ? 40.0 : (card.victory() ? 5.0 : 2.0);
    }

    // A card you cannot foreseeably pay to play just dilutes the deck. Scale the positive play
    // value
    // by how affordable it is: fully if you can pay now, partly if a couple of turns of production
    // income would cover it, heavily discounted if it is out of reach.
    double score = playValue * affordabilityFactor(me, card.productionRequirement(), production);

    // ----- Liabilities are NOT discounted by affordability — a growth card you play still starves
    // you. Growth (population/cap) raises floor(pop)*rate upkeep and feeds the surplus-growth
    // snowball, so it is only worth taking when food can already carry the extra mouths.
    if (popAdd > 0 || capAdd > 0) {
      final boolean sustainable =
          KhStateProjection.recurringFood(me, season)
              >= KhStateProjection.upkeepConsumption(me.getResources().population(), season);
      if (category == SurvivalCategory.CRITICAL || category == SurvivalCategory.UNSAFE) {
        score -= 40.0 + 0.4 * (popAdd + capAdd); // do not dig the hole deeper
      } else if (sustainable && category == SurvivalCategory.STRONG) {
        score += 0.15 * (popAdd + capAdd); // more workers only when clearly affordable
      } else {
        score -= 0.1 * (popAdd + capAdd);
      }
    }

    return score;
  }

  /**
   * How much of a card's play value to credit given its production cost: 1.0 if affordable now, a
   * partial credit if a couple of turns of production income would reach it, and a heavy discount
   * if it is out of reach for the foreseeable future.
   */
  private double affordabilityFactor(
      final PlayerState me, final double cost, final double production) {
    if (cost <= production) {
      return 1.0;
    }
    double income = 0.0;
    for (final Season s : Season.values()) {
      income += Math.max(0.0, KhStateProjection.recurringProduction(me, s));
    }
    final double reachableSoon = production + 2.0 * income;
    if (cost <= reachableSoon) {
      return 0.5;
    }
    return 0.12;
  }

  // =====================================================================================
  // COUNCIL — light heuristic (unchanged spirit from the greedy bot)
  // =====================================================================================

  private void actCouncilAdd(final GameEngine engine, final int seat) {
    final PlayerState me = engine.getState().getPlayer(seat);
    if (me.getCouncil().size() < GameEngine.MAX_COUNCIL_SIZE) {
      final Optional<Advisor> affordable =
          me.getCouncilOfferChoices().stream()
              .filter(a -> me.getResources().gold() >= a.hiringCost())
              .min(Comparator.comparingInt(Advisor::hiringCost));
      if (affordable.isPresent()) {
        try {
          engine.hireAdvisor(affordable.get());
          return;
        } catch (final RuntimeException ignored) {
          // fall through to skip
        }
      }
    }
    engine.skipCouncilAdd();
  }

  private void actCouncilMeeting(final GameEngine engine, final int seat) {
    final PlayerState me = engine.getState().getPlayer(seat);
    final CouncilProposal proposal = me.getCurrentProposal();
    if (proposal != null && proposal.kind() == ProposalKind.DECREE) {
      try {
        engine.acceptCouncilProposal();
        return;
      } catch (final RuntimeException ignored) {
        // fall through to pass
      }
    }
    engine.passCouncilProposal();
  }

  // =====================================================================================
  // helpers
  // =====================================================================================

  /**
   * Sum of direct resource-effect modifiers of {@code target} for the given frequency (null=any).
   */
  private static double directOutput(
      final Card card, final ResourceTarget target, final FrequencyType frequency) {
    if (frequency != null && card.frequencyType() != frequency) {
      return 0.0;
    }
    double sum = 0.0;
    for (final Effect e : card.effects()) {
      if (e.target() == target) {
        sum += e.modifier();
      }
    }
    return sum;
  }

  private static boolean isOffensive(final Card card) {
    return card.effects().stream().anyMatch(e -> e.target() instanceof OpponentResourceTarget);
  }

  private void logPlayDecision(
      final GameState state,
      final int seat,
      final PlayPlan best,
      final KhStateEvaluator.Score bestScore,
      final PlayPlan runnerUp,
      final KhStateEvaluator.Score runnerUpScore,
      final int simulated) {
    if (!(config.debug || log.isDebugEnabled())) {
      return;
    }
    log.debug(
        "[KHBOT seat {}] PLAY {} {} | sims={} category={} chosen=[{}]"
            + " total={} survival={} economy={} offense={}"
            + " projMinFood={} projProd={} runway={} | runnerUp=[{}] total={}",
        seat,
        state.getTime().season(),
        state.getPlayer(seat).getResources(),
        simulated,
        bestScore.category(),
        best.describe(),
        fmt(bestScore.total()),
        fmt(bestScore.survival()),
        fmt(bestScore.economy()),
        fmt(bestScore.offense()),
        fmt(bestScore.projectedMinFood()),
        fmt(bestScore.projectedProduction()),
        fmt(bestScore.foodRunway()),
        runnerUp == null ? "-" : runnerUp.describe(),
        runnerUpScore == null ? "-" : fmt(runnerUpScore.total()));
  }

  private static String fmt(final double v) {
    return String.format("%.1f", v);
  }
}
