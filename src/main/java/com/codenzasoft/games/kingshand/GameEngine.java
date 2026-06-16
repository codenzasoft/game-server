package com.codenzasoft.games.kingshand;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.kingshand.notifications.KhGameOverNotification;
import com.codenzasoft.games.kingshand.notifications.KhGameStartedNotification;
import com.codenzasoft.games.kingshand.notifications.KhNotification;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Owns all rule-based mutations of a {@link GameState}: phase/season advancement, table mutations,
 * the per-season card-trigger pass, draw/discard/hand pile mutations, and random-event resolution.
 * {@code GameState} is a passive data carrier; this engine is the only place that should mutate it.
 *
 * <p><b>RANDOM_EVENT phase flow:</b>
 *
 * <ol>
 *   <li>{@link #advancePhase()} transitions into {@link Phase#RANDOM_EVENT} and immediately rolls
 *       one eligible event via the injected {@link EventCatalog}, storing it on the state.
 *   <li>The caller reads the rolled event with {@link #currentEvent()}.
 *   <li>The player picks an {@link EventOption}; the caller passes it to {@link
 *       #applyEventOption(EventOption)}, which applies its effects and advances the phase to {@link
 *       Phase#PLAY}.
 * </ol>
 *
 * <p><b>Winter scaling (KH-6):</b> when the current season is {@link Season#WINTER}, each effect's
 * numeric modifier is multiplied by the current year before it is applied, so winters grow
 * progressively harsher as the run extends.
 */
public class GameEngine implements Game<GameEngine> {

  public static final int MAX_TABLE_SIZE = 5;
  public static final int MAX_HAND_SIZE = 3;
  public static final int REROLL_COST_STEP = 10;
  public static final double DEMOLISH_COST_RATE = 0.20;
  static final int ADD_OFFER_SIZE = 3;

  private static final KhGameStartedNotification GAME_STARTED_NOTIFICATION =
      new KhGameStartedNotification();
  private static final KhGameOverNotification GAME_OVER_NOTIFICATION = new KhGameOverNotification();

  /**
   * Builder for assembling a {@link GameEngine}, optionally with {@link KhObserver} listeners and a
   * notification {@link Executor}. Mirrors the pattern used by {@code HeartsGame.Builder}.
   */
  public static class Builder {
    private UUID id;
    private Player player;
    private GameState state;
    private CardCatalog cardCatalog;
    private EventCatalog eventCatalog;
    private Random rng;
    private final List<KhObserver> listeners = new ArrayList<>();
    private Executor executor;

    public Builder withId(final UUID id) {
      this.id = id;
      return this;
    }

    public Builder withPlayer(final Player player) {
      this.player = player;
      return this;
    }

    public Builder withState(final GameState state) {
      this.state = state;
      return this;
    }

    public Builder withCardCatalog(final CardCatalog cardCatalog) {
      this.cardCatalog = cardCatalog;
      return this;
    }

    public Builder withEventCatalog(final EventCatalog eventCatalog) {
      this.eventCatalog = eventCatalog;
      return this;
    }

    public Builder withRng(final Random rng) {
      this.rng = rng;
      return this;
    }

    public Builder addListener(final KhObserver listener) {
      listeners.add(listener);
      return this;
    }

    public Builder withNotificationExecutor(final Executor executor) {
      this.executor = executor;
      return this;
    }

    public GameEngine build() {
      return new GameEngine(
          id,
          player,
          state,
          cardCatalog != null ? cardCatalog : CardCatalog.defaultCatalog(),
          eventCatalog != null ? eventCatalog : EventCatalog.defaultCatalog(),
          rng != null ? rng : new Random(),
          listeners,
          executor != null ? executor : Runnable::run);
    }
  }

  private final UUID id;
  private final Player player;
  private final GameState state;
  private final CardCatalog cardCatalog;
  private final EventCatalog eventCatalog;
  private final Random rng;
  private final List<KhObserver> listeners;
  private final Executor executor;

  /** Convenience constructor for tests: injects state, event catalog, and RNG. */
  public GameEngine(GameState state, EventCatalog eventCatalog, Random rng) {
    this(null, null, state, CardCatalog.defaultCatalog(), eventCatalog, rng);
  }

  /** Constructor for restoring a persisted game with its id and player. */
  public GameEngine(UUID id, Player player, GameState state) {
    this(
        id,
        player,
        state,
        CardCatalog.defaultCatalog(),
        EventCatalog.defaultCatalog(),
        new Random());
  }

  /** Full constructor with all dependencies injected, no listeners. */
  GameEngine(
      UUID id,
      Player player,
      GameState state,
      CardCatalog cardCatalog,
      EventCatalog eventCatalog,
      Random rng) {
    this(id, player, state, cardCatalog, eventCatalog, rng, List.of(), Runnable::run);
  }

  /** Full constructor with all dependencies injected, including listeners and an executor. */
  GameEngine(
      UUID id,
      Player player,
      GameState state,
      CardCatalog cardCatalog,
      EventCatalog eventCatalog,
      Random rng,
      List<KhObserver> listeners,
      Executor executor) {
    if (state == null) throw new IllegalArgumentException("state cannot be null");
    if (cardCatalog == null) throw new IllegalArgumentException("catalog cannot be null");
    if (eventCatalog == null) throw new IllegalArgumentException("catalog cannot be null");
    if (rng == null) throw new IllegalArgumentException("rng cannot be null");
    this.id = id;
    this.player = player;
    this.state = state;
    this.cardCatalog = cardCatalog;
    this.eventCatalog = eventCatalog;
    this.rng = rng;
    this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
    this.executor = executor != null ? executor : Runnable::run;
  }

  /** Notifies every registered {@link KhObserver} of the given notification via the executor. */
  private void notifyListeners(final KhNotification notification) {
    final GameEngine game = this;
    final GameState currentState = state;
    for (final KhObserver listener : listeners) {
      executor.execute(() -> notification.execute(game, currentState, listener));
    }
  }

  /**
   * Initializes a brand-new game: seeds the starting deck and offers the first ADD choices. Must be
   * called once after construction for a fresh game. Has no effect if the game is already over.
   *
   * <p>Called by {@code GameService} after building the engine. The initialization logic lives here
   * so the engine owns all knowledge of how a game starts.
   */
  public synchronized void initialize() {
    if (state.isGameOver()) return;
    seedStartingDeck();
    if (state.getCurPhase() == Phase.ADD && state.getAddChoices().isEmpty()) {
      offerAddChoices();
    }
    notifyListeners(GAME_STARTED_NOTIFICATION);
  }

  @Override
  public GameType getGameType() {
    return GameType.KH;
  }

  @Override
  public GameEngine getGame() {
    return this;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public List<Player> getPlayers() {
    return player == null ? List.of() : List.of(player);
  }

  public GameState getState() {
    return state;
  }

  // ----- Phase / season progression -----

  public synchronized void advancePhase() {
    if (state.isGameOver() || state.isGameWon()) {
      return;
    }
    // if play phase ends, move to the next season
    if (state.getCurPhase() == Phase.PLAY) {
      advanceSeason();
    }

    state.setCurPhase(state.getCurPhase().next());

    switch (state.getCurPhase()) {
      case ADD -> offerAddChoices();
      case RANDOM_EVENT -> rollEvent();
      case PLAY -> startPlay();
    }
  }

  // ----- RANDOM_EVENT resolution -----

  /**
   * Randomly selects one eligible event for the current season and year and stores it on the state.
   * If no events are eligible the stored event is set to {@code null}.
   */
  private synchronized void rollEvent() {
    List<Event> eligible = eventCatalog.eligibleEvents(state.getCurSeason(), state.getYear());
    state.setCurrentEvent(eligible.isEmpty() ? null : eligible.get(rng.nextInt(eligible.size())));
  }

  /**
   * Returns the event rolled at the start of the current {@link Phase#RANDOM_EVENT} turn, or {@code
   * null} if no eligible events existed (or if the engine has not yet entered that phase).
   */
  public synchronized Event currentEvent() {
    return state.getCurrentEvent();
  }

  /**
   * Applies the effect list of the chosen {@link EventOption} to the current game state, then
   * advances the phase to {@link Phase#PLAY}.
   *
   * <p>Winter scaling applies: each effect modifier is multiplied by {@link GameState#getYear()}
   * when the current season is {@link Season#WINTER}.
   *
   * @param option the option chosen by the player; must not be {@code null}
   * @throws IllegalArgumentException if {@code option} is {@code null}
   */
  public synchronized void applyEventOption(EventOption option) {
    if (option == null) {
      throw new IllegalArgumentException("option cannot be null");
    }

    // Validate that the player can afford the production cost before applying anything.
    // Simulate production effects (with winter scaling) to check the final value would
    // not go below zero — mirroring the same guard in placeCard().
    double simulatedProduction = state.getProduction();
    for (Effect effect : option.effects()) {
      if (effect.target() instanceof ResourceTarget rt && rt == ResourceTarget.PRODUCTION) {
        simulatedProduction = scaleForSeason(effect).apply(simulatedProduction);
      }
    }
    if (simulatedProduction < 0) {
      throw new IllegalStateException(
          "not enough production to choose this option: have "
              + (int) state.getProduction()
              + " but option requires "
              + (int) -simulatedProduction
              + " more");
    }

    for (Effect effect : option.effects()) {
      if (effect.target() instanceof ResourceTarget rt) {
        Effect scaled = scaleForSeason(effect);
        switch (rt) {
          case POPULATION -> applyPopulation(scaled.apply(state.getPopulation()));
          case PRODUCTION -> applyProduction(scaled.apply(state.getProduction()));
          case FOOD -> applyFood(scaled.apply(state.getFood()));
          case POPULATION_CAP -> {} // passive — not applied via events
          case GOLD -> applyGold(scaled.apply(state.getGold()));
          case STRENGTH -> applyStrength(scaled.apply(state.getStrength()));
        }
      }
      // CardTarget effects are not yet handled here — same deferral as triggerCurrentSeasonTable
    }

    // Only advance to PLAY if the game is still running (population may have hit zero above).
    if (!state.isGameOver()) {
      state.setCurPhase(Phase.PLAY);
      startPlay();
    }
  }

  /**
   * Applies the {@link EventOption} whose {@link EventOption#description()} matches {@code name}
   * (case-sensitive). Delegates to {@link #applyEventOption(EventOption)} after locating the
   * option.
   *
   * @throws IllegalStateException if no current event exists or no option matches the given name
   */
  public synchronized void applyEventOptionByName(String name) {
    Event event = state.getCurrentEvent();
    if (event == null) {
      throw new IllegalStateException("no current event to pick an option from");
    }
    EventOption option =
        event.options().stream()
            .filter(o -> o.description().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no event option with name: " + name));
    applyEventOption(option);
  }

  /**
   * Returns a scaled copy of {@code effect} whose modifier is multiplied by the season-and-year
   * multiplier from {@link #computeScaleMultiplier(Season, int)}. If the multiplier is exactly
   * {@code 1.0} the original effect is returned unchanged to avoid unnecessary allocation.
   *
   * <p>Replaces the old KH-6 linear winter-only rule ({@code modifier × year}).
   */
  private Effect scaleForSeason(Effect effect) {
    double multiplier = computeScaleMultiplier(state.getCurSeason(), state.getYear());
    if (multiplier == 1.0) return effect;
    return new Effect(effect.target(), effect.modifier() * multiplier, effect.valueType());
  }

  /**
   * Computes the event-effect scaling multiplier for the given season and year.
   *
   * <p><b>Winter</b> uses a piecewise accelerating curve so early winters are survivable but late
   * winters become genuinely threatening:
   *
   * <ul>
   *   <li>Years 1–5: linear ramp {@code 1.0 + (year − 1) × 0.15} → 1.00 / 1.15 / 1.30 / 1.45 / 1.60
   *   <li>Years 6+: exponential {@code 1.6 × 1.25^(year − 5)}, capped at {@code 20.0} (~year 16)
   * </ul>
   *
   * <p><b>Spring, Summer, Fall</b> use a slow linear crawl so non-winter events gradually tighten
   * but never dominate: {@code 1.0 + (year − 1) × 0.05} → ~2.0× at year 21.
   *
   * @param season the season in which the event fires
   * @param year the current game year (1-indexed)
   * @return the multiplier to apply to each effect modifier
   */
  double computeScaleMultiplier(Season season, int year) {
    if (season == Season.WINTER) {
      if (year <= 4) {
        // Linear ramp: 1.0 → 2.0 over years 1–4 (doubles by year 4).
        return 1.0 + (year - 1) * (1.0 / 3.0);
      } else {
        // Exponential from 2.0 at year 5, capped at 20× (~year 13).
        double raw = 2.0 * Math.pow(1.30, year - 4);
        return Math.min(raw, 20.0);
      }
    } else {
      return 1.0 + (year - 1) * 0.05;
    }
  }

  public synchronized void advanceSeason() {
    // new year if we end winter
    if (state.getCurSeason() == Season.WINTER) {
      state.setYear(state.getYear() + 1);
    }

    state.setCurSeason(state.getCurSeason().next());
  }

  // ----- Table mutations -----

  public synchronized void addCard(Season season, Card card) {
    List<Card> table = state.getBoard().get(season);
    if (table.size() >= MAX_TABLE_SIZE) {
      throw new IllegalStateException(season + " table is full");
    }
    table.add(card);
  }

  public synchronized void removeCard(Season season, Card card) {
    state.getBoard().get(season).remove(card);
  }

  // ----- Season trigger -----

  protected synchronized void triggerCurrentSeasonTable() {
    List<Card> table = state.getCurrentTable();

    // Walk every card on the current season's table. Only RECURRING cards fire here; frequency is
    // checked once per card (it is a card-level property). The full end-of-PLAY resolver —
    // including SINGLE-frequency cards (engine builders) and post-fire discard — lives in
    // endAndRun.
    for (int i = 0; i < table.size(); i++) {
      Card card = table.get(i);
      if (card.frequencyType() != FrequencyType.RECURRING) continue;
      for (Effect effect : card.effects()) {
        if (effect.target() instanceof ResourceTarget rt) {
          switch (rt) {
            case POPULATION -> applyPopulation(effect.apply(state.getPopulation()));
            case PRODUCTION -> applyProduction(effect.apply(state.getProduction()));
            case FOOD -> applyFood(effect.apply(state.getFood()));
            case POPULATION_CAP -> {} // passive
            case GOLD -> applyGold(effect.apply(state.getGold()));
            case STRENGTH -> applyStrength(effect.apply(state.getStrength()));
          }
        } else if (effect.target() instanceof CardTarget ct) {
          // RECURRING engine builder: permanently boost matching cards to the right.
          for (int j = i + 1; j < table.size(); j++) {
            Card target = table.get(j);
            boolean hasMatch =
                target.effects().stream()
                    .anyMatch(
                        e ->
                            e.target() == ct.targetResource()
                                && e.valueType() == ValueType.INTEGER);
            if (hasMatch) {
              table.set(j, target.boostEffect(ct.targetResource(), effect.modifier()));
              if (ct.adjacentOnly()) break;
            }
          }
        }
      }
    }
  }

  // ----- End-of-PLAY resolution -----

  /**
   * Ends the player's turn: discards every card remaining in the hand to the current season's
   * discard pile, then resolves the current season's table left-to-right using the worker staffing
   * system.
   *
   * <p>Workers ({@code floor(population)}) are distributed left-to-right. Each card's {@link
   * Card#staffRequirement()} determines its {@link StaffingTier}:
   *
   * <ul>
   *   <li>{@link StaffingTier#FULL} — fires at full value.
   *   <li>{@link StaffingTier#HALF} — fires with every effect modifier floored to half.
   *   <li>{@link StaffingTier#NONE} — does not fire.
   * </ul>
   *
   * <p>{@link FrequencyType#SINGLE} cards are always discarded after this phase, even if they did
   * not fire due to insufficient staffing. {@link FrequencyType#RECURRING} cards remain on the
   * table regardless of their tier.
   *
   * <p>Note: {@link CardTarget} effects are not yet evaluated and are silently skipped.
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#PLAY}
   */
  public synchronized void endAndRun() {
    requirePhase(Phase.PLAY, "endAndRun");

    // 1. Discard everything remaining in hand.
    state.discardHand();

    // 2. Clear the great-work event log for this resolution pass.
    List<String> gwLog = new ArrayList<>();
    state.setGreatWorkLog(List.of());

    // 3. Compute staffing based on current population (locked in at End and Run time).
    List<CardStaffing> staffing = computeTableStaffing();

    // 4. Snapshot the table order before resolution so that removals during the walk don't
    //    disturb the iteration sequence.
    List<Card> snapshot = new ArrayList<>(state.getCurrentTable());

    // ── Single left-to-right resolution pass ─────────────────────────────────────
    List<Card> liveTable = state.getCurrentTable();
    int discardedCount = 0;
    List<ResolutionStep> log = new ArrayList<>();

    for (int i = 0; i < snapshot.size(); i++) {
      Card card = snapshot.get(i);
      StaffingTier tier = i < staffing.size() ? staffing.get(i).tier() : StaffingTier.NONE;

      double popBefore = state.getPopulation();
      double prodBefore = state.getProduction();
      double foodBefore = state.getFood();
      double capBefore = state.getPopulationCap();

      if (card.isGreatWork()) {
        // Construction cards are never discarded and never run normal effects.
        // They only progress when fully staffed and all construction costs are affordable.
        if (tier == StaffingTier.FULL && canAffordConstructionCosts(card.effects())) {
          // Apply the per-season construction costs.
          for (Effect effect : card.effects()) {
            if (effect.target() instanceof ResourceTarget rt) {
              switch (rt) {
                case PRODUCTION -> applyProduction(effect.apply(state.getProduction()));
                case FOOD -> applyFood(effect.apply(state.getFood()));
                case GOLD -> applyGold(effect.apply(state.getGold()));
                case STRENGTH -> applyStrength(effect.apply(state.getStrength()));
                default -> {}
              }
            }
          }

          int newPeriod = card.constructionPeriod() - 1;
          if (newPeriod == 0) {
            // ── Construction COMPLETE — swap for the completed card ──────────
            Card completedCard = card.completedCard();
            snapshot.set(i, completedCard);
            liveTable.set(i - discardedCount, completedCard);
            // Unlock any new resource types.
            for (ResourceTarget resource : card.unlocksResources()) {
              if (resource == ResourceTarget.GOLD && !state.isGoldUnlocked()) {
                state.setGoldUnlocked(true);
                gwLog.add(card.name() + "_UNLOCKED_GOLD");
              } else if (resource == ResourceTarget.STRENGTH && !state.isStrengthUnlocked()) {
                state.setStrengthUnlocked(true);
                gwLog.add(card.name() + "_UNLOCKED_STRENGTH");
              }
            }
            gwLog.add(card.name() + "_COMPLETED");
            checkWinCondition();
          } else {
            // Decrement: replace the card on the table with the same card minus one period.
            Card decremented = card.withConstructionPeriod(newPeriod);
            snapshot.set(i, decremented);
            liveTable.set(i - discardedCount, decremented);
            gwLog.add(card.name() + "_PERIOD_DECREMENTED:" + newPeriod);
          }
        } else {
          gwLog.add(card.name() + "_CONSTRUCTION_STALLED");
        }

        log.add(
            new ResolutionStep(
                card.name(),
                tier,
                false,
                state.getPopulation() - popBefore,
                state.getProduction() - prodBefore,
                state.getFood() - foodBefore,
                state.getPopulationCap() - capBefore));
        // Great-work construction/completed cards are NEVER discarded by the engine.
        continue;
      }

      // ── Normal card resolution ────────────────────────────────────────────────

      // Whether all effects on this card are engine-builder (CardTarget) effects.
      boolean isEngineBoost =
          tier != StaffingTier.NONE
              && card.effects().stream().allMatch(e -> e.target() instanceof CardTarget);

      if (tier != StaffingTier.NONE) {
        List<Effect> effects =
            tier == StaffingTier.HALF ? halveEffects(card.effects()) : card.effects();

        for (Effect effect : effects) {
          if (effect.target() instanceof ResourceTarget rt) {
            switch (rt) {
              case POPULATION -> applyPopulation(effect.apply(state.getPopulation()));
              case PRODUCTION -> applyProduction(effect.apply(state.getProduction()));
              case FOOD -> applyFood(effect.apply(state.getFood()));
              case POPULATION_CAP ->
                  state.setPopulationCap(state.getPopulationCap() + effect.modifier());
              case GOLD -> applyGold(effect.apply(state.getGold()));
              case STRENGTH -> applyStrength(effect.apply(state.getStrength()));
            }
          } else if (effect.target() instanceof CardTarget ct) {
            for (int j = i + 1; j < snapshot.size(); j++) {
              Card target = snapshot.get(j);
              boolean hasMatch =
                  target.effects().stream()
                      .anyMatch(
                          e ->
                              e.target() == ct.targetResource()
                                  && e.valueType() == ValueType.INTEGER);
              if (hasMatch) {
                Card boosted = target.boostEffect(ct.targetResource(), effect.modifier());
                snapshot.set(j, boosted);
                liveTable.set(j - discardedCount, boosted);
                if (ct.adjacentOnly()) break;
              }
            }
          }
        }
      }

      log.add(
          new ResolutionStep(
              card.name(),
              tier,
              isEngineBoost,
              state.getPopulation() - popBefore,
              state.getProduction() - prodBefore,
              state.getFood() - foodBefore,
              state.getPopulationCap() - capBefore));

      if (card.frequencyType() == FrequencyType.SINGLE) {
        state.discardFromTable(card);
        discardedCount++;
      }
    }

    // 5. Food consumption / starvation / growth — captured as a tail step.
    double popBefore = state.getPopulation();
    double foodBefore = state.getFood();
    consumeFood();
    log.add(
        new ResolutionStep(
            null,
            null,
            false,
            state.getPopulation() - popBefore,
            0,
            state.getFood() - foodBefore,
            0));

    state.setResolutionLog(log);
    state.setGreatWorkLog(gwLog);

    // 6. Advance to the next phase (ADD for next season, or stay put if game is over / won).
    if (!state.isGameWon()) {
      advancePhase();
    }
  }

  /**
   * Distributes {@code floor(population)} workers left-to-right across the current season's table,
   * filling each card's {@link Card#staffRequirement()} greedily in slot order, and returns the
   * resulting {@link CardStaffing} list (one entry per table slot).
   *
   * <p>The half threshold for {@link StaffingTier#HALF} is {@code ceil(staffRequirement / 2)}.
   */
  List<CardStaffing> computeTableStaffing() {
    List<Card> table = state.getCurrentTable();
    int remaining = (int) Math.floor(state.getPopulation());
    List<CardStaffing> result = new ArrayList<>(table.size());

    for (Card card : table) {
      int cost = (int) card.staffRequirement();
      int allocated = Math.min(remaining, cost);
      remaining -= allocated;

      int halfThreshold = (int) Math.ceil(cost / 2.0);
      StaffingTier tier;
      if (allocated >= cost) {
        tier = StaffingTier.FULL;
      } else if (allocated >= halfThreshold) {
        tier = StaffingTier.HALF;
      } else {
        tier = StaffingTier.NONE;
      }

      result.add(new CardStaffing(allocated, cost, tier));
    }

    return result;
  }

  /**
   * Computes staffing for the current table and stores it on the state so the client can render the
   * live staffing indicator. Call after any mutation that changes table order or contents during
   * the PLAY phase.
   */
  private void computeAndSetStaffing() {
    state.setTableStaffing(computeTableStaffing());
  }

  /**
   * Returns a new effect list with every modifier floored to half, used when a card resolves at
   * {@link StaffingTier#HALF}. Both INTEGER and PERCENTAGE modifiers are halved.
   */
  private List<Effect> halveEffects(List<Effect> effects) {
    return effects.stream()
        .map(e -> new Effect(e.target(), Math.floor(e.modifier() / 2.0), e.valueType()))
        .toList();
  }

  /**
   * Applies a population change, clamping to zero and setting game-over if the result reaches zero.
   * All engine-level population mutations route through this method so game-over detection is never
   * scattered.
   */
  private void applyPopulation(double value) {
    // Clamp between zero and the current population cap.
    double clamped = Math.max(0.0, Math.min(value, state.getPopulationCap()));
    state.setPopulation(clamped);
    // A fractional population (< 1) means nobody is actually alive.
    if (clamped < 1.0 && !state.isGameOver()) {
      state.setPopulation(0.0);
      state.setGameOver(true);
      notifyListeners(GAME_OVER_NOTIFICATION);
    }
  }

  /** Applies a production change, clamping to zero. Production can never go negative. */
  private void applyProduction(double value) {
    state.setProduction(Math.max(0.0, value));
  }

  /**
   * Applies a food change, clamping to zero. If the result would be negative the shortfall is
   * treated as a starvation deficit — food is set to 0 and population drops by {@code 0.5 ×
   * deficit}, mirroring the logic in {@link #consumeFood()}.
   */
  private void applyFood(double value) {
    if (value >= 0) {
      state.setFood(value);
    } else {
      double deficit = -value;
      state.setFood(0.0);
      applyPopulation(state.getPopulation() - 0.5 * deficit);
    }
  }

  /**
   * Consumes food at the end of every PLAY phase.
   *
   * <p>The per-population rate is 2 for {@link Season#WINTER} and 1 for all other seasons. Total
   * consumption is {@code floor(population) × rate}. If food is sufficient it is simply reduced; if
   * food is insufficient, food is clamped to 0 and population drops by {@code 0.5 × deficit}.
   */
  private void consumeFood() {
    int rate = (state.getCurSeason() == Season.WINTER) ? 2 : 1;
    double consumed = Math.floor(state.getPopulation()) * rate;
    double available = state.getFood();
    if (available >= consumed) {
      state.setFood(available - consumed);
    } else {
      double deficit = consumed - available;
      state.setFood(0.0);
      applyPopulation(state.getPopulation() - 0.5 * deficit);
    }

    // Surplus growth: if food remains after consumption, grow population proportionally.
    // Growth rate is capped at 20% per season so the population snowball stays manageable.
    // The surplus food is not consumed — it carries over to the next season.
    if (!state.isGameOver() && state.getFood() > 0 && state.getPopulation() > 0) {
      double growthRate = Math.min(state.getFood() / state.getPopulation(), 0.20);
      applyPopulation(state.getPopulation() + state.getPopulation() * growthRate);
    }
  }

  // ----- ADD phase: card offer, pick, skip -----

  /**
   * Offers three uniformly-random cards drawn from the current season's catalog.
   *
   * <p>The <em>first</em> call each ADD phase is free. Every subsequent call within the same phase
   * is a paid reroll: the cost of the <em>n</em>-th reroll (1-indexed) is {@code n × }{@value
   * #REROLL_COST_STEP} production, so costs escalate as 10, 20, 30, … The offer counter is reset to
   * zero by {@link #pickCard(Card)} and {@link #skipAdd()}, so it is always phase-scoped.
   *
   * @return the three offered cards
   * @throws IllegalStateException if the current phase is not {@link Phase#ADD}, or if this is a
   *     reroll the player cannot afford
   */
  public synchronized List<Card> offerAddChoices() {
    requirePhase(Phase.ADD, "offerAddChoices");
    if (state.getRerollCount() > 0) {
      int cost = state.getRerollCount() * REROLL_COST_STEP;
      if (state.getProduction() < cost) {
        throw new IllegalStateException(
            "not enough production to reroll: need "
                + cost
                + ", have "
                + (int) state.getProduction());
      }
      applyProduction(state.getProduction() - cost);
    }
    state.setRerollCount(state.getRerollCount() + 1);
    // Filter out cards whose effects target locked resources (GOLD / STRENGTH), and Great Works
    // that have already been drafted/commissioned/completed — each may only ever exist once.
    List<Card> available =
        cardCatalog.forSeason(state.getCurSeason()).stream()
            .filter(c -> !cardRequiresLockedResource(c))
            .filter(c -> !c.isGreatWork() || !greatWorkAlreadyClaimed(c))
            .toList();
    List<Card> choices = pickRandom(available, ADD_OFFER_SIZE);
    state.setAddChoices(choices);
    return choices;
  }

  /**
   * Shuffles {@code card} into the current season's draw pile and advances the phase to {@link
   * Phase#RANDOM_EVENT}. Clears the offer list and resets the reroll counter.
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#ADD}
   */
  public synchronized void pickCard(Card card) {
    requirePhase(Phase.ADD, "pickCard");
    addToDeck(state.getCurSeason(), card);
    clearAddState();
    state.setCurrentEvent(null);
    state.setCurPhase(Phase.RANDOM_EVENT);
    rollEvent();
  }

  /**
   * Picks the offered ADD-phase card whose {@link Card#name()} matches {@code name}
   * (case-sensitive). Delegates to {@link #pickCard(Card)} after locating the card.
   *
   * @throws IllegalStateException if no offered card matches the given name
   */
  public synchronized void pickCardByName(String name) {
    Card card =
        state.getAddChoices().stream()
            .filter(c -> c.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no offered card with name: " + name));
    pickCard(card);
  }

  /**
   * Skips the ADD phase without adding a card, advancing the phase to {@link Phase#RANDOM_EVENT}.
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#ADD}
   */
  public synchronized void skipAdd() {
    requirePhase(Phase.ADD, "skipAdd");
    clearAddState();
    state.setCurrentEvent(null);
    state.setCurPhase(Phase.RANDOM_EVENT);
    rollEvent();
  }

  // ----- ADD helpers -----

  /**
   * Returns {@code true} if any of the card's effects target a resource that has not yet been
   * unlocked ({@link ResourceTarget#GOLD} before TREASURY completion, or {@link
   * ResourceTarget#STRENGTH} before BARRACKS completion). Such cards are suppressed from the
   * ADD-phase offer until the corresponding Great Work is finished.
   */
  private boolean cardRequiresLockedResource(Card card) {
    for (Effect effect : card.effects()) {
      if (effect.target() instanceof ResourceTarget rt) {
        if (rt == ResourceTarget.GOLD && !state.isGoldUnlocked()) return true;
        if (rt == ResourceTarget.STRENGTH && !state.isStrengthUnlocked()) return true;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} if Great Work {@code gw} (or its completed counterpart) already exists
   * anywhere in the game — hand, any draw pile, any discard pile, or any season's table. Each
   * Great Work may only ever be drafted or commissioned once, so once claimed it is suppressed
   * from the ADD-phase offer pool and blocked from being commissioned again, no matter which path
   * (draft-and-place, or direct commission) put it there.
   */
  private boolean greatWorkAlreadyClaimed(Card gw) {
    if (cardExistsAnywhere(gw.name())) return true;
    return gw.completedCard() != null && cardExistsAnywhere(gw.completedCard().name());
  }

  /** Returns {@code true} if a card named {@code name} exists in the hand or any pile/table. */
  private boolean cardExistsAnywhere(String name) {
    if (state.getHand().stream().anyMatch(c -> c.name().equals(name))) return true;
    for (List<Card> cards : state.getBoard().values()) {
      if (cards.stream().anyMatch(c -> c.name().equals(name))) return true;
    }
    for (List<Card> cards : state.getDrawPile().values()) {
      if (cards.stream().anyMatch(c -> c.name().equals(name))) return true;
    }
    for (List<Card> cards : state.getDiscardPile().values()) {
      if (cards.stream().anyMatch(c -> c.name().equals(name))) return true;
    }
    return false;
  }

  private void requirePhase(Phase required, String operation) {
    if (state.getCurPhase() != required) {
      throw new IllegalStateException(
          operation
              + " requires phase "
              + required
              + " but current phase is "
              + state.getCurPhase());
    }
  }

  private void clearAddState() {
    state.setAddChoices(List.of());
    state.setRerollCount(0);
  }

  /** Returns {@code count} distinct cards chosen uniformly at random from {@code source}. */
  private List<Card> pickRandom(List<Card> source, int count) {
    List<Card> pool = new ArrayList<>(source);
    Collections.shuffle(pool, rng);
    return List.copyOf(pool.subList(0, Math.min(count, pool.size())));
  }

  // ----- Pile mutations -----

  /**
   * Seeds the per-season starting deck into the draw piles for a brand-new game. Must be called
   * once after construction before the first ADD phase. Uses {@link
   * CardCatalog#defaultStartingDeck()} so the composition is kept in one place.
   */
  public synchronized void seedStartingDeck() {
    CardCatalog.defaultStartingDeck().forEach(this::addToDeck);
  }

  // when we add a card to one of our draws, add it to the drawpile and shuffle the drawpile.
  public synchronized void addToDeck(Season season, Card card) {
    addToDeck(season, List.of(card));
  }

  // add multiple cards and shuffle.
  public synchronized void addToDeck(Season season, List<Card> cards) {
    if (season == null) {
      throw new IllegalStateException("season cannot be null");
    }
    if (cards == null) {
      throw new IllegalStateException("cards cannot be null");
    }

    state.getDrawPile().get(season).addAll(cards);
    Collections.shuffle(state.getDrawPile().get(season));
  }

  // draw n cards, if drawpile empty, shuffle discard into draw.
  public synchronized List<Card> draw(Season season, int n) {
    if (season == null) {
      throw new IllegalStateException("season cannot be null");
    }
    if (n < 0) {
      throw new IllegalStateException("cannot draw a negative number of cards");
    }

    List<Card> drawnCards = new ArrayList<>();

    for (int i = 0; i < n; i++) {
      if (state.getDrawPile().get(season).isEmpty()) {
        reshuffleDiscardIntoDrawPile(season);
      }

      List<Card> pile = state.getDrawPile().get(season);
      if (!pile.isEmpty()) {

        Card drawnCard = pile.remove(0);
        state.getHand().add(drawnCard);
        drawnCards.add(drawnCard);
      }
    }

    return drawnCards;
  }

  // move discard to draw pile, clear discard pile, shuffle draw pile.
  private synchronized void reshuffleDiscardIntoDrawPile(Season season) {
    state.getDrawPile().get(season).addAll(state.getDiscardPile().get(season));
    state.getDiscardPile().get(season).clear();
    Collections.shuffle(state.getDrawPile().get(season));
  }

  // ----- PLAY phase: draw hand, place card, demolish card -----

  /**
   * Draws up to {@value #MAX_HAND_SIZE} cards from the current season's draw pile into the hand,
   * auto-reshuffling the discard pile whenever the draw pile empties mid-draw. If the combined draw
   * + discard count is less than {@value #MAX_HAND_SIZE}, the hand receives whatever cards are
   * available.
   *
   * @return the newly drawn cards (may be fewer than {@value #MAX_HAND_SIZE})
   * @throws IllegalStateException if the current phase is not {@link Phase#PLAY}
   */
  public synchronized List<Card> startPlay() {
    requirePhase(Phase.PLAY, "startPlay");
    List<Card> drawn = draw(state.getCurSeason(), MAX_HAND_SIZE);
    // Compute initial staffing snapshot when the Play phase begins.
    computeAndSetStaffing();
    return drawn;
  }

  /**
   * Places {@code card} from the player's hand onto the current season's table, after validating:
   *
   * <ol>
   *   <li>The card is present in the hand.
   *   <li>The table has an open slot (<{@value #MAX_TABLE_SIZE} cards) <em>or</em> the card
   *       duplicates one already on the table (upgrade path).
   *   <li>Current production ≥ {@link Card#productionRequirement()}.
   * </ol>
   *
   * <p>Cards can be played regardless of population — staffing is evaluated at resolution time by
   * {@link #endAndRun()}, not at placement. On success the production cost is deducted and the card
   * is removed from the hand. If a matching card already sits on the table, every effect's modifier
   * on that table card is doubled (upgrade) rather than adding a new slot.
   *
   * @param card the card to place; must not be {@code null}
   * @throws IllegalArgumentException if {@code card} is {@code null}
   * @throws IllegalStateException if the current phase is not {@link Phase#PLAY}, or if any
   *     validation check fails
   */
  // we will eventually want to be able to choose where this card is placed with an index parameter.
  public synchronized void placeCard(Card card) {
    requirePhase(Phase.PLAY, "placeCard");

    if (card == null) {
      throw new IllegalArgumentException("card cannot be null");
    }

    if (!state.getHand().contains(card)) {
      throw new IllegalStateException("card is not in hand");
    }

    List<Card> table = state.getCurrentTable();

    // if there is no card to upgrade and no space on the table, you cannot play this card, reject.
    if (!state.tableContains(card) && table.size() >= MAX_TABLE_SIZE) {
      throw new IllegalStateException(state.getCurSeason() + " table is full");
    }

    // if you cannot afford the production cost, reject
    if (state.getProduction() < card.productionRequirement()) {
      throw new IllegalStateException(
          "not enough production to place card: need "
              + (int) card.productionRequirement()
              + ", have "
              + (int) state.getProduction());
    }

    // Pay the production cost and remove the card from the hand.
    applyProduction(state.getProduction() - card.productionRequirement());
    state.getHand().remove(card);

    // If a matching card is already on the table, merge the hand card into it so that
    // engine-boosted modifiers are preserved: Farm(14) + Farm(10) → Farm II(24).
    // Otherwise just add it to the table as a new slot.
    if (state.tableContains(card)) {
      Card tableCard = state.findTableCard(card.name());
      state.replaceTableCard(tableCard, tableCard.mergeWith(card));
    } else {
      state.addToTable(card);
    }

    // Recompute staffing so the client sees the updated allocation immediately.
    computeAndSetStaffing();
  }

  /**
   * Places the hand card whose {@link Card#name()} matches {@code name} (case-sensitive). Delegates
   * to {@link #placeCard(Card)} after locating the card.
   *
   * @throws IllegalStateException if no hand card matches the given name
   */
  public synchronized void placeCardByName(String name) {
    Card card =
        state.getHand().stream()
            .filter(c -> c.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no hand card with name: " + name));
    placeCard(card);
  }

  /**
   * Demolishes the table card whose {@link Card#name()} matches {@code name} (case-sensitive).
   * Delegates to {@link #demolishCard(int)} after locating the slot.
   *
   * @throws IllegalStateException if no table card matches the given name
   */
  public synchronized void demolishCardByName(String name) {
    List<Card> table = state.getCurrentTable();
    for (int i = 0; i < table.size(); i++) {
      if (table.get(i).name().equals(name)) {
        demolishCard(i);
        return;
      }
    }
    throw new IllegalStateException("no table card with name: " + name);
  }

  /**
   * Reorders the current season's table to match the provided list of card names. Every card
   * currently on the table must appear in {@code cardNames} exactly once; the table is then sorted
   * to match the given order.
   *
   * <p>This replaces the old swap-based {@code reorderCard(fromSlot, toSlot)} API. The client sends
   * the complete desired order after each drag, so a single call always reflects the player's
   * intent regardless of how many intermediate drags occurred.
   *
   * @param cardNames the desired order of card names; must match the current table contents exactly
   * @throws IllegalStateException if the current phase is not {@link Phase#PLAY}, if the size
   *     doesn't match, or if any name doesn't correspond to a table card
   */
  public synchronized void reorderTable(List<String> cardNames) {
    requirePhase(Phase.PLAY, "reorderTable");

    List<Card> table = state.getCurrentTable();

    if (cardNames.size() != table.size()) {
      throw new IllegalStateException(
          "reorderTable: expected " + table.size() + " names but got " + cardNames.size());
    }

    // Build a lookup from name → card, then reconstruct the table in the requested order.
    // Use a mutable copy so we can remove as we match, catching any duplicate-name edge cases.
    List<Card> remaining = new ArrayList<>(table);
    List<Card> reordered = new ArrayList<>(table.size());

    for (String name : cardNames) {
      Card match =
          remaining.stream()
              .filter(c -> c.name().equals(name))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException("reorderTable: no table card with name: " + name));
      reordered.add(match);
      remaining.remove(match);
    }

    // Replace table contents in-place so the existing list reference stays valid.
    table.clear();
    table.addAll(reordered);

    // Recompute staffing so the client sees the updated priority chain immediately.
    computeAndSetStaffing();
  }

  /**
   * Demolishes the card at the given {@code slot} index on the current season's table, charging
   * {@value #DEMOLISH_COST_RATE} × the card's original {@link Card#productionRequirement()} as the
   * demolition cost and freeing the slot.
   *
   * @param slot zero-based index into the current season's table
   * @throws IllegalStateException if the current phase is not {@link Phase#PLAY}, if {@code slot}
   *     is out of range, or if the player cannot afford the cost
   */
  public synchronized void demolishCard(int slot) {
    requirePhase(Phase.PLAY, "demolishCard");

    List<Card> table = state.getCurrentTable();

    if (slot < 0 || slot >= table.size()) {
      throw new IllegalStateException("invalid slot: " + slot);
    }

    Card card = table.get(slot);
    double cost = card.productionRequirement() * DEMOLISH_COST_RATE;

    // if you cannot afford the production cost, reject.
    if (state.getProduction() < cost) {
      throw new IllegalStateException(
          "not enough production to demolish: need " + cost + ", have " + state.getProduction());
    }

    // else pay and demolish card
    applyProduction(state.getProduction() - cost);
    state.removeFromTableAt(slot);

    // Recompute staffing so freed workers are redistributed immediately.
    computeAndSetStaffing();
  }

  // ----- Great Work commissioning -----

  /**
   * Commissions the Great Work identified by {@code name}, placing its under-construction card on
   * the current season's table and deducting the one-time commission fee.
   *
   * <p>Preconditions (all enforced; throw {@link IllegalStateException} on violation):
   *
   * <ul>
   *   <li>Current phase is {@link Phase#PLAY}.
   *   <li>The Great Work's {@link GreatWork#tableSlot()} matches the current season.
   *   <li>The Great Work has not already been drafted (sitting in a draw/discard pile or hand),
   *       commissioned, or completed — each Great Work may only ever exist once.
   *   <li>The current season's table has at least one open slot.
   *   <li>The player has enough production to cover the commission cost (the under-construction
   *       card's {@link Card#productionRequirement()}).
   *   <li>If the under-construction card's own effects include a {@link ResourceTarget#GOLD} or
   *       {@link ResourceTarget#STRENGTH} cost, that resource must already be unlocked — the same
   *       rule {@code cardRequiresLockedResource} applies to the ADD-phase offer pool. Great Works
   *       with no such cost (e.g. THE_GREAT_RESERVOIR) are always commissionable.
   * </ul>
   *
   * @param name stable name of the Great Work (e.g. {@code "TREASURY"})
   * @throws IllegalStateException on any precondition violation
   * @throws IllegalArgumentException if no Great Work with {@code name} exists in the catalog
   */
  public synchronized void commissionGreatWork(String name) {
    requirePhase(Phase.PLAY, "commissionGreatWork");

    Card gw = cardCatalog.getByName(name);
    if (gw == null || !gw.isGreatWork()) {
      throw new IllegalArgumentException("no great work with name: " + name);
    }

    if (gw.tableSlot() != state.getCurSeason()) {
      throw new IllegalStateException(
          "cannot commission "
              + name
              + " during "
              + state.getCurSeason()
              + " — it belongs on the "
              + gw.tableSlot()
              + " table");
    }

    // Already drafted (sitting in a draw pile, discard pile, or hand), already commissioned
    // (construction card on a table), or already completed (completed card on a table). A Great
    // Work may only ever exist once, regardless of which path put it there.
    if (greatWorkAlreadyClaimed(gw)) {
      throw new IllegalStateException("great work already drafted, commissioned, or completed: " + name);
    }

    List<Card> targetTable = state.getBoard().get(gw.tableSlot());

    if (targetTable.size() >= MAX_TABLE_SIZE) {
      throw new IllegalStateException(gw.tableSlot() + " table is full");
    }

    // A Great Work can only be commissioned once any resource its own construction cost
    // references (GOLD/STRENGTH) is unlocked — the same rule that gates the ADD-phase offer pool.
    // Great Works with no such cost (e.g. THE_GREAT_RESERVOIR) are always commissionable.
    if (cardRequiresLockedResource(gw)) {
      throw new IllegalStateException(
          "commissioning " + name + " requires its locked resource(s) to be unlocked first");
    }

    // Pay the one-time commission cost.
    double cost = gw.productionRequirement();
    if (state.getProduction() < cost) {
      throw new IllegalStateException(
          "not enough production to commission "
              + name
              + ": need "
              + (int) cost
              + ", have "
              + (int) state.getProduction());
    }
    applyProduction(state.getProduction() - cost);

    targetTable.add(gw);
    computeAndSetStaffing();
  }

  // ----- Great Work helpers -----

  /**
   * Simulates whether the player can currently afford all construction-cost effects without any
   * resource going below zero. Returns {@code true} if every resource would remain {@code >= 0}
   * after applying every effect.
   *
   * <p>Only {@link ResourceTarget} effects are evaluated here; {@link CardTarget} effects are
   * ignored (they don't appear on construction cards).
   */
  private boolean canAffordConstructionCosts(List<Effect> effects) {
    double simProd = state.getProduction();
    double simFood = state.getFood();
    double simGold = state.getGold();
    double simStrength = state.getStrength();

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

  /**
   * Checks whether all four win-condition {@link GreatWork}s are completed and, if so, sets {@link
   * GameState#setGameWon(boolean)} to {@code true}. Called after every Great Work completes.
   */
  /**
   * Sets {@link GameState#setGameWon(boolean)} to {@code true} when every win-condition card has
   * completed. A completed win-condition card is identified by {@link Card#winCondition()} {@code
   * == true} and {@link Card#isGreatWork()} {@code == false} (meaning it is the permanent completed
   * version, not the construction card).
   */
  private void checkWinCondition() {
    long completedWinConditions =
        state.getBoard().values().stream()
            .flatMap(List::stream)
            .filter(c -> c.winCondition() && !c.isGreatWork())
            .count();
    if (completedWinConditions
        >= CardCatalog.ALL_GREAT_WORKS.stream().filter(Card::winCondition).count()) {
      state.setGameWon(true);
    }
  }

  /**
   * Returns all {@link GreatWork}s from the catalog whose {@link GreatWork#tableSlot()} matches the
   * given season and that have not yet been commissioned. Used by clients to populate the Great
   * Works commissioning menu.
   *
   * @param season the season whose available Great Works to list
   * @return Great Works available for commissioning in {@code season}
   */
  /**
   * Returns Great Work construction cards available for commissioning on {@code season}'s table —
   * i.e. cards that haven't already been drafted, commissioned, or completed anywhere in the
   * game. Each Great Work may only ever exist once.
   */
  public synchronized List<Card> availableGreatWorks(Season season) {
    return cardCatalog.greatWorksForSeason(season).stream()
        .filter(gw -> !greatWorkAlreadyClaimed(gw))
        .toList();
  }

  // ----- New resource application -----

  /**
   * Applies a gold change, clamping to zero. No-ops if gold is not yet unlocked (TREASURY not
   * complete).
   */
  private void applyGold(double value) {
    if (!state.isGoldUnlocked()) return;
    state.setGold(Math.max(0.0, value));
  }

  /**
   * Applies a strength change, clamping to zero. No-ops if strength is not yet unlocked (BARRACKS
   * not complete).
   */
  private void applyStrength(double value) {
    if (!state.isStrengthUnlocked()) return;
    state.setStrength(Math.max(0.0, value));
  }
}
