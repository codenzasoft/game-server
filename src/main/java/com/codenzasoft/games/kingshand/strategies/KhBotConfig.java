package com.codenzasoft.games.kingshand.strategies;

/**
 * Tunable configuration for {@link KhBeamSearchStrategy}: search shape, safety reserves, evaluation
 * weights, the RNG seed, and the debug flag. Kept as one immutable object (built via {@link
 * Builder}) so a balance run can spin up several differently-tuned bots and pit them against each
 * other without touching the strategy code.
 *
 * <p>Nothing in the search or the evaluator hardcodes a search parameter or weight — they all read
 * from here — so tuning is a config change, not a code change.
 */
public final class KhBotConfig {

  // ----- Search shape -----

  /** How many upcoming seasons the food/production projection looks ahead when scoring a state. */
  public final int projectionHorizonSeasons;

  /** Beam width: how many top candidate PLAY plans survive to be fully evaluated. */
  public final int beamWidth;

  /** Hard cap on simulated resolutions per PLAY decision, so a decision always stays fast. */
  public final int maxSimulationsPerDecision;

  /** Distinct table orderings tried per candidate placement (the FERTILIZER-ordering fix). */
  public final int tableOrderingsPerPlan;

  // ----- Safety reserves (multiples of one upkeep's food consumption) -----

  /** At/under this many upkeeps of food on hand → CRITICAL (starvation imminent). */
  public final double criticalFoodUpkeeps;

  /** Under this many upkeeps of food runway → UNSAFE (survives next upkeep, no margin). */
  public final double unsafeFoodUpkeeps;

  /** At/over this many upkeeps of runway with positive recurring food → STRONG (play to win). */
  public final double strongFoodUpkeeps;

  /** Production floor below which the kingdom is treated as production-starved. */
  public final double productionFloor;

  // ----- Evaluation weights -----

  /** Penalty applied to a state that predictably starves inside the projection horizon. */
  public final double starvationPenalty;

  /** Reward/penalty for a decided match (win positive, loss negative). Dominates all else. */
  public final double terminalMagnitude;

  public final double foodReserveWeight;
  public final double foodRunwayWeight;
  public final double recurringFoodWeight;
  public final double productionWeight;
  public final double recurringProductionWeight;
  public final double populationWeight;
  public final double populationHeadroomWeight;
  public final double playableCardWeight;
  public final double deadCardPenalty;
  public final double greatWorkProgressWeight;
  public final double opponentPressureWeight;

  // ----- Reproducibility / diagnostics -----

  public final long seed;
  public final boolean debug;

  private KhBotConfig(final Builder b) {
    this.projectionHorizonSeasons = b.projectionHorizonSeasons;
    this.beamWidth = b.beamWidth;
    this.maxSimulationsPerDecision = b.maxSimulationsPerDecision;
    this.tableOrderingsPerPlan = b.tableOrderingsPerPlan;
    this.criticalFoodUpkeeps = b.criticalFoodUpkeeps;
    this.unsafeFoodUpkeeps = b.unsafeFoodUpkeeps;
    this.strongFoodUpkeeps = b.strongFoodUpkeeps;
    this.productionFloor = b.productionFloor;
    this.starvationPenalty = b.starvationPenalty;
    this.terminalMagnitude = b.terminalMagnitude;
    this.foodReserveWeight = b.foodReserveWeight;
    this.foodRunwayWeight = b.foodRunwayWeight;
    this.recurringFoodWeight = b.recurringFoodWeight;
    this.productionWeight = b.productionWeight;
    this.recurringProductionWeight = b.recurringProductionWeight;
    this.populationWeight = b.populationWeight;
    this.populationHeadroomWeight = b.populationHeadroomWeight;
    this.playableCardWeight = b.playableCardWeight;
    this.deadCardPenalty = b.deadCardPenalty;
    this.greatWorkProgressWeight = b.greatWorkProgressWeight;
    this.opponentPressureWeight = b.opponentPressureWeight;
    this.seed = b.seed;
    this.debug = b.debug;
  }

  /** The canonical, balance-tested-defaults config. */
  public static KhBotConfig defaults() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Returns a copy of this config with a different RNG seed (for reproducible test fixtures). */
  public KhBotConfig withSeed(final long newSeed) {
    return toBuilder().seed(newSeed).build();
  }

  /** Returns a copy of this config with debug logging toggled. */
  public KhBotConfig withDebug(final boolean newDebug) {
    return toBuilder().debug(newDebug).build();
  }

  public Builder toBuilder() {
    return new Builder()
        .projectionHorizonSeasons(projectionHorizonSeasons)
        .beamWidth(beamWidth)
        .maxSimulationsPerDecision(maxSimulationsPerDecision)
        .tableOrderingsPerPlan(tableOrderingsPerPlan)
        .criticalFoodUpkeeps(criticalFoodUpkeeps)
        .unsafeFoodUpkeeps(unsafeFoodUpkeeps)
        .strongFoodUpkeeps(strongFoodUpkeeps)
        .productionFloor(productionFloor)
        .starvationPenalty(starvationPenalty)
        .terminalMagnitude(terminalMagnitude)
        .foodReserveWeight(foodReserveWeight)
        .foodRunwayWeight(foodRunwayWeight)
        .recurringFoodWeight(recurringFoodWeight)
        .productionWeight(productionWeight)
        .recurringProductionWeight(recurringProductionWeight)
        .populationWeight(populationWeight)
        .populationHeadroomWeight(populationHeadroomWeight)
        .playableCardWeight(playableCardWeight)
        .deadCardPenalty(deadCardPenalty)
        .greatWorkProgressWeight(greatWorkProgressWeight)
        .opponentPressureWeight(opponentPressureWeight)
        .seed(seed)
        .debug(debug);
  }

  /** Builder carrying the default weights; override only what a balance run wants to change. */
  public static final class Builder {
    private int projectionHorizonSeasons = 4;
    private int beamWidth = 6;
    private int maxSimulationsPerDecision = 64;
    private int tableOrderingsPerPlan = 3;

    private double criticalFoodUpkeeps = 1.0;
    private double unsafeFoodUpkeeps = 2.5;
    private double strongFoodUpkeeps = 4.0;
    private double productionFloor = 100.0;

    private double starvationPenalty = 1_000_000.0;
    private double terminalMagnitude = 1_000_000_000.0;

    // Survival factors dominate; economy is secondary; offense is a tie-breaker until STRONG.
    private double foodReserveWeight = 0.5;
    private double foodRunwayWeight = 4_000.0;
    private double recurringFoodWeight = 40.0;
    private double productionWeight = 0.3;
    private double recurringProductionWeight = 30.0;
    private double populationWeight = 30.0;
    private double populationHeadroomWeight = 5.0;
    private double playableCardWeight = 25.0;
    private double deadCardPenalty = 60.0;
    private double greatWorkProgressWeight = 250.0;
    private double opponentPressureWeight = 1.0;

    private long seed = 20260804L;
    private boolean debug = false;

    public Builder projectionHorizonSeasons(final int v) {
      this.projectionHorizonSeasons = v;
      return this;
    }

    public Builder beamWidth(final int v) {
      this.beamWidth = v;
      return this;
    }

    public Builder maxSimulationsPerDecision(final int v) {
      this.maxSimulationsPerDecision = v;
      return this;
    }

    public Builder tableOrderingsPerPlan(final int v) {
      this.tableOrderingsPerPlan = v;
      return this;
    }

    public Builder criticalFoodUpkeeps(final double v) {
      this.criticalFoodUpkeeps = v;
      return this;
    }

    public Builder unsafeFoodUpkeeps(final double v) {
      this.unsafeFoodUpkeeps = v;
      return this;
    }

    public Builder strongFoodUpkeeps(final double v) {
      this.strongFoodUpkeeps = v;
      return this;
    }

    public Builder productionFloor(final double v) {
      this.productionFloor = v;
      return this;
    }

    public Builder starvationPenalty(final double v) {
      this.starvationPenalty = v;
      return this;
    }

    public Builder terminalMagnitude(final double v) {
      this.terminalMagnitude = v;
      return this;
    }

    public Builder foodReserveWeight(final double v) {
      this.foodReserveWeight = v;
      return this;
    }

    public Builder foodRunwayWeight(final double v) {
      this.foodRunwayWeight = v;
      return this;
    }

    public Builder recurringFoodWeight(final double v) {
      this.recurringFoodWeight = v;
      return this;
    }

    public Builder productionWeight(final double v) {
      this.productionWeight = v;
      return this;
    }

    public Builder recurringProductionWeight(final double v) {
      this.recurringProductionWeight = v;
      return this;
    }

    public Builder populationWeight(final double v) {
      this.populationWeight = v;
      return this;
    }

    public Builder populationHeadroomWeight(final double v) {
      this.populationHeadroomWeight = v;
      return this;
    }

    public Builder playableCardWeight(final double v) {
      this.playableCardWeight = v;
      return this;
    }

    public Builder deadCardPenalty(final double v) {
      this.deadCardPenalty = v;
      return this;
    }

    public Builder greatWorkProgressWeight(final double v) {
      this.greatWorkProgressWeight = v;
      return this;
    }

    public Builder opponentPressureWeight(final double v) {
      this.opponentPressureWeight = v;
      return this;
    }

    public Builder seed(final long v) {
      this.seed = v;
      return this;
    }

    public Builder debug(final boolean v) {
      this.debug = v;
      return this;
    }

    public KhBotConfig build() {
      return new KhBotConfig(this);
    }
  }
}
