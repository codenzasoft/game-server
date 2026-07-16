package com.codenzasoft.games.kingshand;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.GameLifecycleObserver;
import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.kingshand.notifications.KhGameOverNotification;
import com.codenzasoft.games.kingshand.notifications.KhGameStartedNotification;
import com.codenzasoft.games.kingshand.notifications.KhGameUpdatedNotification;
import com.codenzasoft.games.kingshand.notifications.KhNotification;
import com.codenzasoft.games.kingshand.strategies.KhPlayerStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns all rule-based mutations of a {@link GameState}: phase/season advancement, table mutations,
 * the per-season card-trigger pass, draw/discard/hand pile mutations, and the Council/Advisors
 * system. {@code GameState} is a passive data carrier; this engine is the only place that should
 * mutate it.
 *
 * <p><b>Phase flow.</b> There is no generic phase cycle — see {@link Phase}'s javadoc. Every
 * phase-completing action below is seat-scoped and gated by the <em>readiness barrier</em>: it
 * performs the acting seat's work, marks that seat submitted, and only when every live seat has
 * submitted does the shared clock advance (exactly once) and the next phase get seeded for every
 * seat. With a single seat the barrier releases immediately, preserving single-player behavior. The
 * Council is inserted once per year, immediately after Winter's {@link Phase#PLAY} ends, but only
 * once GOLD has been unlocked (TREASURY completed) in <em>either</em> kingdom:
 *
 * <ol>
 *   <li>{@link Phase#ADD} → {@link Phase#PLAY} via {@link #pickCard(Card)} / {@link #skipAdd()}.
 *   <li>{@link Phase#PLAY} → {@link #endAndRun()} calls {@link #advancePhase()}, which enters
 *       {@link Phase#COUNCIL_ADD} (if Winter just ended and GOLD is unlocked) or goes straight to
 *       {@link Phase#ADD} otherwise.
 *   <li>{@link Phase#COUNCIL_ADD} → {@link Phase#COUNCIL_MEETING} via {@link #hireAdvisor(Advisor)}
 *       / {@link #hireAdvisorById(String)} / {@link #skipCouncilAdd()}.
 *   <li>{@link Phase#COUNCIL_MEETING} → {@link Phase#ADD} via {@link #acceptCouncilProposal()} /
 *       {@link #passCouncilProposal()}.
 * </ol>
 *
 * <p><b>Advisors</b>: once seated (see {@link #hireAdvisor(Advisor)}), a {@link SeatedAdvisor}'s
 * card effects fire unconditionally and in full every season as part of {@link #endAndRun()} —
 * never subject to the table's {@link StaffingTier} degradation, and seating one costs nothing
 * beyond the upfront hiring fee. See {@link Advisor}/{@link AdvisorCatalog} for the roster and
 * {@link Reform}/ {@link Decree} for the two project types proposed once a year during {@link
 * Phase#COUNCIL_MEETING}.
 */
public class GameEngine implements Game<GameEngine> {

  public static final int MAX_TABLE_SIZE = 5;
  public static final int MAX_HAND_SIZE = 3;
  public static final int REROLL_COST_STEP = 50;
  public static final double DEMOLISH_COST_RATE = 0.20;
  static final int ADD_OFFER_SIZE = 3;

  /** Maximum number of advisors that may be seated on the council at once. */
  public static final int MAX_COUNCIL_SIZE = 3;

  static final int COUNCIL_OFFER_SIZE = 3;

  /**
   * Fraction of an advisor's original {@link Advisor#hiringCost()} charged in gold to dismiss them
   * from the council ("let them go"). Mirrors {@link #DEMOLISH_COST_RATE}'s role for table cards.
   */
  public static final double ADVISOR_DISMISSAL_COST_RATE = 0.20;

  private static final KhGameStartedNotification GAME_STARTED_NOTIFICATION =
      new KhGameStartedNotification();
  private static final KhGameUpdatedNotification GAME_UPDATED_NOTIFICATION =
      new KhGameUpdatedNotification();
  private static final KhGameOverNotification GAME_OVER_NOTIFICATION = new KhGameOverNotification();

  /**
   * Builder for assembling a {@link GameEngine}, optionally with {@link KhObserver} listeners and a
   * notification {@link Executor}. Mirrors the pattern used by {@code HeartsGame.Builder}.
   */
  public static class Builder {
    private UUID id;
    private Player player;
    private List<Player> players;
    private GameState state;
    private CardCatalog cardCatalog;
    private Random rng;
    private final List<KhObserver> listeners = new ArrayList<>();
    private final List<GameLifecycleObserver<GameEngine>> lifecycleObservers = new ArrayList<>();
    private final Map<Integer, KhPlayerStrategy> strategies = new LinkedHashMap<>();
    private Executor executor;

    public Builder withId(final UUID id) {
      this.id = id;
      return this;
    }

    public Builder withPlayer(final Player player) {
      this.player = player;
      return this;
    }

    /**
     * Registers the seat-ordered player identities for a multi-seat match (seat {@code i} = {@code
     * players.get(i)}). Takes precedence over {@link #withPlayer(Player)} when both are set.
     */
    public Builder withPlayers(final List<Player> players) {
      this.players = players;
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

    public Builder withRng(final Random rng) {
      this.rng = rng;
      return this;
    }

    public Builder addListener(final KhObserver listener) {
      listeners.add(listener);
      return this;
    }

    /**
     * Registers a common {@link GameLifecycleObserver}, notified of game-type-agnostic lifecycle
     * events (started, resumed, over, quit) alongside the KH-specific {@link KhObserver}s.
     */
    public Builder addLifecycleObserver(final GameLifecycleObserver<GameEngine> observer) {
      lifecycleObservers.add(observer);
      return this;
    }

    public Builder withNotificationExecutor(final Executor executor) {
      this.executor = executor;
      return this;
    }

    /**
     * Registers a {@link KhPlayerStrategy} to drive {@code seat}'s kingdom (a bot seat). Mirrors
     * {@code HeartsGame.Builder.addPlayerObserver(player, strategy)}. Seats with a strategy are
     * auto-driven by the engine after every phase seed (see {@link GameEngine#driveBots()}).
     */
    public Builder withStrategy(final int seat, final KhPlayerStrategy strategy) {
      strategies.put(seat, strategy);
      return this;
    }

    public GameEngine build() {
      final List<Player> seatPlayers =
          players != null ? players : (player != null ? List.of(player) : List.of());
      final GameEngine engine =
          new GameEngine(
              id,
              seatPlayers,
              state,
              cardCatalog != null ? cardCatalog : CardCatalog.defaultCatalog(),
              rng != null ? rng : new Random(),
              listeners,
              executor != null ? executor : Runnable::run,
              lifecycleObservers);
      strategies.forEach(engine::registerStrategy);
      return engine;
    }
  }

  private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

  private final UUID id;
  private final List<Player> players;
  private final GameState state;
  private final CardCatalog cardCatalog;
  private final Random rng;
  private final List<KhObserver> listeners;
  private final List<GameLifecycleObserver<GameEngine>> lifecycleObservers;
  private final Executor executor;

  /**
   * Per-seat bot drivers ({@link KhPlayerStrategy}), keyed by seat. Seats without an entry are
   * human-driven via the WS layer. See {@link #driveBots()}.
   */
  private final Map<Integer, KhPlayerStrategy> strategies = new HashMap<>();

  /**
   * Seat whose kingdom the engine is currently acting on. Actions are per-kingdom (each player
   * places/plays/ends on their own board), so the WS layer sets this to the requesting player's
   * seat before invoking an action. Defaults to seat 0, which keeps single-kingdom games and the
   * existing single-player tests operating exactly as before.
   */
  private int activeSeat = 0;

  /** Convenience constructor for tests: injects state and RNG. */
  public GameEngine(GameState state, Random rng) {
    this(
        null,
        List.<Player>of(),
        state,
        CardCatalog.defaultCatalog(),
        rng,
        List.of(),
        Runnable::run,
        List.of());
  }

  /** Constructor for restoring a persisted single-player game with its id and player. */
  public GameEngine(UUID id, Player player, GameState state) {
    this(
        id,
        seatList(player),
        state,
        CardCatalog.defaultCatalog(),
        new Random(),
        List.of(),
        Runnable::run,
        List.of());
  }

  /** Full constructor with all dependencies injected, no listeners. */
  GameEngine(UUID id, Player player, GameState state, CardCatalog cardCatalog, Random rng) {
    this(id, seatList(player), state, cardCatalog, rng, List.of(), Runnable::run, List.of());
  }

  /** Full constructor with all dependencies injected, including listeners and an executor. */
  GameEngine(
      UUID id,
      Player player,
      GameState state,
      CardCatalog cardCatalog,
      Random rng,
      List<KhObserver> listeners,
      Executor executor) {
    this(id, seatList(player), state, cardCatalog, rng, listeners, executor, List.of());
  }

  /** Wraps a possibly-null single player into a seat-ordered list. */
  private static List<Player> seatList(final Player player) {
    return player == null ? List.of() : List.of(player);
  }

  /**
   * Full constructor with all dependencies injected, including the seat-ordered player identities,
   * {@link KhObserver} listeners, an executor, and common {@link GameLifecycleObserver}s.
   */
  GameEngine(
      UUID id,
      List<Player> players,
      GameState state,
      CardCatalog cardCatalog,
      Random rng,
      List<KhObserver> listeners,
      Executor executor,
      List<GameLifecycleObserver<GameEngine>> lifecycleObservers) {
    if (state == null) throw new IllegalArgumentException("state cannot be null");
    if (cardCatalog == null) throw new IllegalArgumentException("catalog cannot be null");
    if (rng == null) throw new IllegalArgumentException("rng cannot be null");
    this.id = id;
    this.players = players == null ? List.of() : List.copyOf(players);
    this.state = state;
    this.cardCatalog = cardCatalog;
    this.rng = rng;
    this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
    this.executor = executor != null ? executor : Runnable::run;
    this.lifecycleObservers =
        lifecycleObservers == null ? List.of() : List.copyOf(lifecycleObservers);
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
   * Notifies every registered common {@link GameLifecycleObserver} via the executor. {@code call}
   * is typically a method reference, e.g. {@code GameLifecycleObserver::gameStarted}.
   */
  private void notifyLifecycleObservers(
      final BiConsumer<GameLifecycleObserver<GameEngine>, Game<GameEngine>> call) {
    final Game<GameEngine> game = this;
    for (final GameLifecycleObserver<GameEngine> observer : lifecycleObservers) {
      executor.execute(() -> call.accept(observer, game));
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
    if (activePlayer().isGameOver()) return;
    seedStartingDeck();
    if (state.getTime().phase() == Phase.ADD) {
      forEachLiveSeat(
          seat -> {
            if (activePlayer().getAddChoices().isEmpty()) {
              offerAddChoices();
            }
          });
    }
    // Bot seats act (and submit) as soon as the first phase is seeded, so the initial broadcast
    // already shows them ready and a lone human is never blocked on an idle bot.
    driveBots();
    notifyListeners(GAME_STARTED_NOTIFICATION);
    // TODO: KH does not yet persist mid-game state (GameService.buildKhGame always rebuilds a
    // fresh GameState), so there is no reliable signal to distinguish a true resume from this
    // first build. gameResumed() exists on GameLifecycleObserver and can be fired here once that
    // persistence gap is closed; for now every (re)build is treated as a fresh start.
    notifyLifecycleObservers(GameLifecycleObserver::gameStarted);
  }

  /**
   * Ends the game because a player quit before it was otherwise over. No-ops if the game is already
   * over. Reuses the existing {@link GameState#isGameOver()} flag rather than tracking a separate
   * quit reason, since nothing in KH's gameplay logic needs to distinguish "ended naturally" from
   * "ended by quit" — only that the game has ended.
   */
  public synchronized void quit() {
    if (activePlayer().isGameOver()) return;
    notifyLifecycleObservers(GameLifecycleObserver::gameQuit);
  }

  /**
   * Forfeits {@code seat}'s kingdom (the "gives up → forfeits" rule): the seat is marked out,
   * removed from the readiness barrier, and — since a forfeited seat counts as knocked out — the
   * match outcome is settled for the remaining seat(s). Fires {@code gameOver} when the forfeit
   * decides the match. No-ops if the seat is already out or the match is already over.
   */
  public synchronized void forfeit(final int seat) {
    if (seat < 0 || seat >= state.playerCount()) {
      throw new IllegalArgumentException("no such seat: " + seat);
    }
    if (state.getPlayer(seat).isGameOver() || state.isMatchOver()) {
      return;
    }
    state.getPlayer(seat).forfeit();
    if (state.isMatchOver()) {
      notifyListeners(GAME_OVER_NOTIFICATION);
      notifyLifecycleObservers(GameLifecycleObserver::gameOver);
    } else if (state.allSubmitted()) {
      // The forfeiting seat may have been the last one the barrier was waiting on.
      resolvePhaseAndAdvance();
    }
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
    return players;
  }

  /**
   * Returns the seat index of the player whose {@link Player#name()} equals {@code username}, or
   * {@code -1} if no such player is seated. Used by the WS layer to map an authenticated request to
   * the seat it should act on.
   */
  public int seatOf(final String username) {
    for (int seat = 0; seat < players.size(); seat++) {
      if (players.get(seat).name().equals(username)) {
        return seat;
      }
    }
    return -1;
  }

  /**
   * Broadcasts the current match state to registered {@link KhObserver}s via {@code gameUpdated}.
   * Called at the request boundary after an action mutates state, so the broadcast layer can push
   * fresh per-seat views without the engine's action methods knowing about transport.
   */
  public synchronized void notifyUpdated() {
    notifyListeners(GAME_UPDATED_NOTIFICATION);
  }

  public GameState getState() {
    return state;
  }

  // ----- Seat targeting -----

  /**
   * Points subsequent actions at {@code seat}'s kingdom. The WS layer resolves the requesting user
   * to their seat and calls this before performing that player's action. Synchronized with the
   * action methods (all {@code synchronized}) so a request's set-then-act is atomic.
   *
   * @throws IllegalArgumentException if {@code seat} is not a valid seat in this match
   */
  public synchronized void setActiveSeat(final int seat) {
    if (seat < 0 || seat >= state.playerCount()) {
      throw new IllegalArgumentException("no such seat: " + seat);
    }
    this.activeSeat = seat;
  }

  /** The seat the engine is currently acting on. */
  public int getActiveSeat() {
    return activeSeat;
  }

  /** The kingdom ({@link PlayerState}) at the {@link #activeSeat}. */
  private PlayerState activePlayer() {
    return state.getPlayer(activeSeat);
  }

  /** The current shared season, from the match clock. */
  private Season season() {
    return state.getTime().season();
  }

  /**
   * Runs {@code perSeat} once for every live (not knocked-out) seat, with {@link #activeSeat}
   * temporarily pointed at that seat so the seat-scoped helpers act on the right kingdom. Restores
   * the previous active seat afterwards.
   */
  private void forEachLiveSeat(final java.util.function.IntConsumer perSeat) {
    final int saved = activeSeat;
    try {
      for (int seat = 0; seat < state.playerCount(); seat++) {
        if (state.getPlayer(seat).isGameOver()) {
          continue;
        }
        activeSeat = seat;
        perSeat.accept(seat);
      }
    } finally {
      activeSeat = saved;
    }
  }

  // ----- Turn coordination: the readiness barrier -----
  //
  // The clock is shared, but each seat acts on its own board independently and asynchronously, so
  // the clock must not advance until ALL live seats have finished the current phase. Every
  // phase-completing action (pickCard/skipAdd, endAndRun, hireAdvisor/skipCouncilAdd,
  // accept/passCouncilProposal) therefore performs its seat-scoped work, marks the seat submitted,
  // and stops. Only when every live seat has submitted does resolvePhaseAndAdvance() run: it
  // advances the shared clock exactly once and seeds the next phase for every seat. With a single
  // seat the barrier releases immediately, so single-player behavior is unchanged.

  /**
   * Throws if the active seat has already submitted the current phase — a submitted seat may take
   * no further phase actions until the barrier releases.
   */
  private void requireNotSubmitted(final String operation) {
    if (state.isSubmitted(activeSeat)) {
      throw new IllegalStateException(
          operation + " rejected: seat " + activeSeat + " has already submitted this phase");
    }
  }

  /**
   * Marks the active seat as done with the current phase and, if that releases the barrier (every
   * live seat submitted), resolves the phase and advances the shared clock once.
   */
  private void submitActiveSeat() {
    state.markSubmitted(activeSeat);
    if (state.allSubmitted()) {
      resolvePhaseAndAdvance();
    }
  }

  /**
   * Runs once per completed phase, after every live seat has submitted: clears the submit marks,
   * performs the phase's shared resolution (for PLAY: every table resolves left-to-right against
   * pre-resolution opponent snapshots, then cross-seat sabotage deltas apply), advances the shared
   * clock exactly once, seeds the next phase for every live seat, and lets bot seats take their
   * next-phase actions. Fires {@code gameOver} (and stops the clock) when the match is decided.
   */
  private void resolvePhaseAndAdvance() {
    final Phase completed = state.getTime().phase();
    state.clearSubmitted();
    switch (completed) {
      case ADD -> {
        state.advanceTime(); // ADD -> PLAY
        forEachLiveSeat(seat -> startPlay());
      }
      case PLAY -> {
        resolvePlayForAllSeats();
        if (state.isMatchOver()) {
          notifyListeners(GAME_OVER_NOTIFICATION);
          notifyLifecycleObservers(GameLifecycleObserver::gameOver);
          return; // the clock stops with the match
        }
        if (state.playerCount() > 1) {
          // Multiplayer: the tables have resolved, but hold the shared clock on the resolved season
          // until every seat clicks Continue on the resolution screen (see continueResolution()).
          // driveBots() below auto-continues bot seats, leaving only human seats to acknowledge.
          state.setResolutionPending(true);
        } else {
          // Single-player: advance immediately — unchanged behavior.
          advancePlayClock();
        }
      }
      case COUNCIL_ADD -> {
        state.advanceTime(); // COUNCIL_ADD -> COUNCIL_MEETING
        forEachLiveSeat(seat -> offerCouncilProposal());
      }
      case COUNCIL_MEETING -> {
        state.advanceTime(); // COUNCIL_MEETING -> ADD
        forEachLiveSeat(seat -> offerAddChoices());
      }
    }
    driveBots();
  }

  /**
   * Advances the clock out of a resolved PLAY phase and seeds the next phase for every live seat.
   */
  private void advancePlayClock() {
    // Deferred single-use discard: SINGLE-frequency cards leave the resolved season's table only
    // now — on Continue in multiplayer — so they stay visible on the resolution screen while it is
    // shown. (In single-player this runs in the same endAndRun call, so the net state is
    // unchanged.)
    final Season resolved = state.getTime().season();
    forEachLiveSeat(seat -> discardSpentSingles(seat, resolved));

    state.advanceTime(); // PLAY -> COUNCIL_ADD (post-Winter, gold unlocked) or ADD
    switch (state.getTime().phase()) {
      case COUNCIL_ADD -> forEachLiveSeat(seat -> offerCouncilChoices());
      case ADD -> forEachLiveSeat(seat -> offerAddChoices());
      default -> {}
    }
  }

  /** Moves every SINGLE-frequency card off {@code seat}'s {@code season} table into the discard. */
  private void discardSpentSingles(final int seat, final Season season) {
    final PlayerState p = state.getPlayer(seat);
    for (final Card card : new ArrayList<>(p.getTable(season))) {
      if (card.frequencyType() == FrequencyType.SINGLE) {
        p.discardFromTable(season, card);
      }
    }
  }

  /**
   * Acknowledges the multiplayer resolution screen for the active seat ("Continue"). While a PLAY
   * resolution is held (see {@link #resolvePhaseAndAdvance()}), this marks the seat done; once
   * every live seat has acknowledged, the held clock finally advances out of the resolved season
   * and the next phase is seeded. No-op when no resolution is pending or the seat has already
   * acknowledged, so a double-click is harmless.
   */
  public synchronized void continueResolution() {
    if (!state.isResolutionPending() || state.isSubmitted(activeSeat)) {
      return;
    }
    state.markSubmitted(activeSeat);
    if (state.allSubmitted()) {
      state.setResolutionPending(false);
      state.clearSubmitted();
      advancePlayClock();
      driveBots();
    }
  }

  // ----- Bot driving -----

  /**
   * Registers a {@link KhPlayerStrategy} to drive {@code seat}'s kingdom. Bot seats go through the
   * exact same seat-aware action methods as humans; the only difference is who triggers them.
   *
   * <p>Internal: strategies are supplied at construction via {@code Builder#withStrategy} and copied
   * onto the engine here during {@code build()}. Not part of the public runtime API.
   */
  private void registerStrategy(final int seat, final KhPlayerStrategy strategy) {
    strategies.put(seat, strategy);
  }

  /**
   * True if {@code seat} is bot-driven (has a registered strategy), and therefore has no connected
   * client to receive state broadcasts.
   */
  public synchronized boolean isBotSeat(final int seat) {
    return strategies.containsKey(seat);
  }

  /**
   * Lets every strategy-driven (bot) seat that still owes an action in the current phase take its
   * turn and submit, so the readiness barrier never stalls on a bot. Invoked after the first phase
   * is seeded ({@link #initialize()}) and after every phase change. If a strategy fails to submit
   * (or throws), the seat's phase is force-completed with the default action so the match can
   * always proceed.
   */
  public synchronized void driveBots() {
    if (state.isMatchOver()) {
      return;
    }
    for (final Map.Entry<Integer, KhPlayerStrategy> entry : strategies.entrySet()) {
      final int seat = entry.getKey();
      if (seat < 0 || seat >= state.playerCount()) continue;
      if (state.getPlayer(seat).isGameOver()) continue;
      if (state.isSubmitted(seat)) continue;
      final TimeState before = state.getTime();
      final int saved = activeSeat;
      try {
        activeSeat = seat;
        if (state.isResolutionPending()) {
          // Held PLAY resolution: the bot acknowledges the Continue barrier instantly, so only
          // human
          // seats keep the resolution screen open.
          continueResolution();
        } else {
          try {
            entry.getValue().act(this, seat);
          } catch (final RuntimeException e) {
            log.error("Bot strategy failed for seat {}: {}", seat, e.getMessage(), e);
          }
          // Safety net: the strategy must end its phase. Only force the default completion if the
          // clock hasn't moved (its own submit may have released the barrier and advanced the
          // phase).
          if (state.getTime().equals(before)
              && !state.isMatchOver()
              && !state.getPlayer(seat).isGameOver()
              && !state.isSubmitted(seat)) {
            defaultSubmit();
          }
        }
      } finally {
        activeSeat = saved;
      }
      if (state.isMatchOver()) {
        return;
      }
    }
  }

  /** Completes the active seat's current phase with the default no-frills action. */
  private void defaultSubmit() {
    switch (state.getTime().phase()) {
      case ADD -> skipAdd();
      case PLAY -> endAndRun();
      case COUNCIL_ADD -> skipCouncilAdd();
      case COUNCIL_MEETING -> passCouncilProposal();
    }
  }

  // ----- Phase / season progression -----

  /**
   * Called when {@link Phase#PLAY} ends (from {@link #endAndRun()}). Advances the season/year, then
   * either opens the Council (after Winter, once GOLD is unlocked) or goes straight back to {@link
   * Phase#ADD}. No-ops outside of {@link Phase#PLAY}, and never does anything once the game is over
   * or won.
   */
  protected synchronized void advancePhase() {
    if (activePlayer().isGameOver() || activePlayer().isVictoryComplete()) {
      return;
    }
    if (state.getTime().phase() != Phase.PLAY) {
      return;
    }
    state.advanceTime();
    switch (state.getTime().phase()) {
      case COUNCIL_ADD:
        offerCouncilChoices();
        break;
      case ADD:
        offerAddChoices();
        break;
    }
  }

  // ----- Table mutations -----

  public synchronized void addCard(Season season, Card card) {
    List<Card> table = activePlayer().getBoard().get(season);
    if (table.size() >= MAX_TABLE_SIZE) {
      throw new IllegalStateException(season + " table is full");
    }
    table.add(card);
  }

  public synchronized void removeCard(Season season, Card card) {
    activePlayer().getBoard().get(season).remove(card);
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

    if (activePlayer().isGameOver()) {
      return;
    }

    requirePhase(Phase.PLAY, "endAndRun");
    requireNotSubmitted("endAndRun");

    // Seat-scoped completion intent only: mark this seat done with PLAY. The actual table
    // resolution (resolveSeat) runs for every kingdom at once when the barrier releases, so both
    // tables "fire left-to-right at the same time". With a single seat this is immediate.
    submitActiveSeat();
  }

  /**
   * Resolves the active seat's current-season table — the body of the old single-player {@code
   * endAndRun}, minus the phase advance. Discards the hand, resolves the table left-to-right under
   * the worker staffing system, fires seated advisors, applies the cross-table council boost, and
   * consumes food. Sabotage ({@link OpponentResourceTarget}) effects are resolved against {@code
   * sabotage}'s frozen opponent snapshot and only <em>collected</em>; the caller applies them after
   * every kingdom's self-resolution completes.
   */
  private void resolveSeat(final SabotageContext sabotage) {

    // 1. Discard everything remaining in hand.
    activePlayer().discardHand(season());

    // 2. Compute staffing based on current population (locked in at End and Run time).
    final List<CardStaffing> staffing = activePlayer().getTableStaffing(season());

    // 3. Snapshot the table order before resolution so that updates during the walk don't
    //    disturb the iteration sequence.
    final List<Card> snapshot = new ArrayList<>(activePlayer().getTable(season()));

    // 4. Play each card on the table updating resource state, from left to right.
    final List<ResolutionStep> log = new ArrayList<>();

    for (int i = 0; i < snapshot.size(); i++) {
      final Card card = snapshot.get(i);
      final StaffingTier tier = staffing.get(i).tier();
      final ResourceState before = activePlayer().getResources();

      // Whether all effects on this card are engine-builder (CardTarget) effects.
      boolean isEngineBoost =
          tier != StaffingTier.NONE
              && card.effects().stream().allMatch(e -> e.target() instanceof CardTarget);

      card.play(activePlayer(), season(), tier, sabotage);
      log.add(
          new ResolutionStep(
              card.name(), tier, isEngineBoost, activePlayer().getResources().getDelta(before)));

      // NOTE: SINGLE-frequency cards are NOT discarded here. They leave the table only when the
      // clock advances (advancePlayClock → discardSpentSingles), so they stay on the table — and
      // therefore visible on the multiplayer resolution screen — for the whole held-resolution
      // window.
    }

    // 4.5. Seated council resolution — advisors bypass staffing entirely: every seated advisor's
    // card effects fire unconditionally, at full value, every season. See class javadoc.
    for (SeatedAdvisor seated : activePlayer().getCouncil()) {
      Card card = seated.getCard();
      ResourceState before = activePlayer().getResources();

      card.play(activePlayer(), season(), StaffingTier.FULL, sabotage);
      log.add(
          new ResolutionStep(
              card.name(),
              StaffingTier.FULL,
              false,
              activePlayer().getResources().getDelta(before)));
    }

    // Brynn Ostlar (and any future advisor with a cross-table boost): permanently boosts the first
    // matching Production card on the season table that is just about to resolve next time around.
    double crossTableBoost = seatedCrossTableBoost();
    if (crossTableBoost > 0) {
      List<Card> currentTable = activePlayer().getTable(season());
      for (int j = 0; j < currentTable.size(); j++) {
        final Card target = currentTable.get(j);
        boolean hasMatch =
            target.effects().stream()
                .anyMatch(
                    e ->
                        e.target() == ResourceTarget.PRODUCTION
                            && e.valueType() == ValueType.INTEGER);
        if (hasMatch) {
          currentTable.set(j, target.boostEffect(ResourceTarget.PRODUCTION, crossTableBoost));
          log.add(
              new ResolutionStep(
                  "COUNCIL_CROSS_TABLE_BOOST", StaffingTier.FULL, true, ResourceState.empty()));
          break;
        }
      }
    }

    // 5. Food consumption / starvation / growth — captured as a tail step.
    final ResourceState before = activePlayer().getResources();
    consumeFood();
    log.add(new ResolutionStep(null, null, false, activePlayer().getResources().getDelta(before)));

    activePlayer().setResolutionLog(log);
  }

  /**
   * Shared PLAY resolution for the whole match, run once when the readiness barrier releases.
   * Implements the locked sabotage rule: snapshot every kingdom <em>before</em> any table resolves,
   * resolve each table against the frozen opponent snapshot, then apply the collected cross-seat
   * deltas only after all self-resolution completes — deterministic and order-independent.
   */
  private void resolvePlayForAllSeats() {
    final int seatCount = state.playerCount();

    // Pre-resolution snapshot of every kingdom's resources.
    final List<ResourceState> snapshots = new ArrayList<>(seatCount);
    for (int seat = 0; seat < seatCount; seat++) {
      snapshots.add(state.getPlayer(seat).getResources());
    }

    // Per-victim-seat collected sabotage deltas.
    final List<List<Effect>> deltasBySeat = new ArrayList<>(seatCount);
    for (int seat = 0; seat < seatCount; seat++) {
      deltasBySeat.add(new ArrayList<>());
    }

    // Phase one: every live kingdom resolves its own table against frozen opponent snapshots.
    forEachLiveSeat(
        seat -> {
          final int opponent = opponentOf(seat);
          final SabotageContext sabotage =
              opponent < 0
                  ? SabotageContext.none()
                  : new SabotageContext(snapshots.get(opponent), deltasBySeat.get(opponent));
          resolveSeat(sabotage);
        });

    // Phase two: apply the collected cross-seat deltas, appending a sabotage tail step to each
    // victim's resolution log so the client can animate the incoming hit.
    for (int seat = 0; seat < seatCount; seat++) {
      final List<Effect> deltas = deltasBySeat.get(seat);
      if (deltas.isEmpty()) {
        continue;
      }
      final PlayerState victim = state.getPlayer(seat);
      final ResourceState before = victim.getResources();
      for (final Effect delta : deltas) {
        victim.apply(delta);
      }
      final List<ResolutionStep> log = new ArrayList<>(victim.getResolutionLog());
      log.add(
          new ResolutionStep(
              "SABOTAGE", StaffingTier.FULL, false, victim.getResources().getDelta(before)));
      victim.setResolutionLog(log);
    }
  }

  /**
   * The single live opposing seat of {@code seat}, or {@code -1} if none (single-player, or every
   * opponent already knocked out). Two-player matches have at most one opponent; revisit for N>2.
   */
  private int opponentOf(final int seat) {
    for (int other = 0; other < state.playerCount(); other++) {
      if (other != seat && !state.getPlayer(other).isGameOver()) {
        return other;
      }
    }
    return -1;
  }

  /**
   * Consumes food at the end of every PLAY phase.
   *
   * <p>The per-population rate is 2 for {@link Season#WINTER} and 1 for all other seasons. Total
   * consumption is {@code floor(population) × rate}. If food is sufficient it is simply reduced; if
   * food is insufficient, food is clamped to 0 and population drops by {@code 0.5 × deficit}.
   */
  private void consumeFood() {
    int rate = (state.getTime().season() == Season.WINTER) ? 2 : 1;
    activePlayer().consumeFood(rate);
  }

  // ----- ADD phase: card offer, pick, skip -----

  /**
   * Offers three uniformly-random cards drawn from the current season's catalog.
   *
   * <p>The <em>first</em> call each ADD phase is free. Every subsequent call within the same phase
   * is a paid reroll: the cost of the <em>n</em>-th reroll (1-indexed) is {@code n × }{@value
   * #REROLL_COST_STEP} production, so costs escalate as 50, 100, 150, … The offer counter is reset
   * to zero by {@link #pickCard(Card)} and {@link #skipAdd()}, so it is always phase-scoped.
   *
   * @return the three offered cards
   * @throws IllegalStateException if the current phase is not {@link Phase#ADD}, or if this is a
   *     reroll the player cannot afford
   */
  public synchronized List<Card> offerAddChoices() {
    requirePhase(Phase.ADD, "offerAddChoices");
    requireNotSubmitted("offerAddChoices");
    // The first (1 + permanent + seated-bonus) calls each ADD phase are free; every call after that
    // is a paid reroll, escalating by REROLL_COST_STEP per paid call.
    int freeCalls = 1 + activePlayer().getPermanentFreeRerolls() + seatedFreeRerollBonus();
    if (activePlayer().getRerollCount() >= freeCalls) {
      int cost = (activePlayer().getRerollCount() - freeCalls + 1) * REROLL_COST_STEP;
      if (activePlayer().getResources().production() < cost) {
        throw new IllegalStateException(
            "not enough production to reroll: need "
                + cost
                + ", have "
                + (int) activePlayer().getResources().production());
      }
      activePlayer().addProduction(0.0 - cost);
    }
    activePlayer().setRerollCount(activePlayer().getRerollCount() + 1);
    // Filter out cards whose effects target locked resources (GOLD / STRENGTH), and Great Works
    // that have already been drafted/commissioned/completed — each may only ever exist once.
    List<Card> available =
        cardCatalog.forSeason(state.getTime().season()).stream()
            .filter(activePlayer()::areRequiredResourcesUnlocked)
            .filter(c -> !c.isGreatWork() || !greatWorkAlreadyClaimed(c))
            .toList();
    // Lady Iseult Varn (and any future advisor with this trait) reveals extra candidates.
    int offerSize = ADD_OFFER_SIZE + seatedExtraAddCandidates();
    List<Card> choices = pickRandom(available, offerSize);
    activePlayer().setAddChoices(choices);
    return choices;
  }

  /**
   * Shuffles {@code card} into the current season's draw pile and advances the phase to {@link
   * Phase#PLAY}. Clears the offer list and resets the reroll counter.
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#ADD}
   */
  public synchronized void pickCard(Card card) {
    requirePhase(Phase.ADD, "pickCard");
    requireNotSubmitted("pickCard");
    addToDeck(state.getTime().season(), card);
    clearAddState();
    // Readiness barrier: the pick completes this seat's ADD phase; the clock advances (and every
    // seat draws its PLAY hand) only once all live seats have picked or skipped.
    submitActiveSeat();
  }

  /**
   * Picks the offered ADD-phase card whose {@link Card#name()} matches {@code name}
   * (case-sensitive). Delegates to {@link #pickCard(Card)} after locating the card.
   *
   * @throws IllegalStateException if no offered card matches the given name
   */
  public synchronized void pickCardByName(String name) {
    Card card =
        activePlayer().getAddChoices().stream()
            .filter(c -> c.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no offered card with name: " + name));
    pickCard(card);
  }

  /**
   * Skips the ADD phase without adding a card, advancing the phase to {@link Phase#PLAY}.
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#ADD}
   */
  public synchronized void skipAdd() {
    requirePhase(Phase.ADD, "skipAdd");
    requireNotSubmitted("skipAdd");
    clearAddState();
    submitActiveSeat();
  }

  // ----- ADD helpers -----

  /**
   * Returns {@code true} if Great Work {@code gw} (or its completed counterpart) already exists
   * anywhere in the game — hand, any draw pile, any discard pile, or any season's table. Each Great
   * Work may only ever be drafted or commissioned once, so once claimed it is suppressed from the
   * ADD-phase offer pool and blocked from being commissioned again, no matter which path
   * (draft-and-place, or direct commission) put it there.
   */
  private boolean greatWorkAlreadyClaimed(Card gw) {
    if (activePlayer().isCardInPlay(gw)) return true;
    return gw.completedCard() != null && activePlayer().isCardInPlay(gw.completedCard());
  }

  private void requirePhase(Phase required, String operation) {
    if (state.getTime().phase() != required) {
      throw new IllegalStateException(
          operation
              + " requires phase "
              + required
              + " but current phase is "
              + state.getTime().phase());
    }
  }

  /**
   * Like {@link #requirePhase} but accepts either council sub-phase. The roster panel (and its "let
   * go" action) is visible and interactive throughout the whole once-a-year council turn, not just
   * one sub-phase of it.
   */
  private void requireCouncilPhase(String operation) {
    Phase current = state.getTime().phase();
    if (current != Phase.COUNCIL_ADD && current != Phase.COUNCIL_MEETING) {
      throw new IllegalStateException(
          operation
              + " requires phase COUNCIL_ADD or COUNCIL_MEETING but current phase is "
              + current);
    }
  }

  private void clearAddState() {
    activePlayer().setAddChoices(List.of());
    activePlayer().setRerollCount(0);
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
    final int saved = activeSeat;
    try {
      // Seed every kingdom's draw piles, not just the active seat's, so each player in a
      // multi-kingdom match starts with the same standard deck.
      for (int seat = 0; seat < state.playerCount(); seat++) {
        activeSeat = seat;
        CardCatalog.defaultStartingDeck().forEach(this::addToDeck);
      }
    } finally {
      activeSeat = saved;
    }
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

    activePlayer().getDrawPile().get(season).addAll(cards);
    Collections.shuffle(activePlayer().getDrawPile().get(season));
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
      if (activePlayer().getDrawPile().get(season).isEmpty()) {
        reshuffleDiscardIntoDrawPile(season);
      }

      List<Card> pile = activePlayer().getDrawPile().get(season);
      if (!pile.isEmpty()) {

        Card drawnCard = pile.remove(0);
        activePlayer().getHand().add(drawnCard);
        drawnCards.add(drawnCard);
      }
    }

    return drawnCards;
  }

  // move discard to draw pile, clear discard pile, shuffle draw pile.
  private synchronized void reshuffleDiscardIntoDrawPile(Season season) {
    activePlayer().getDrawPile().get(season).addAll(activePlayer().getDiscardPile().get(season));
    activePlayer().getDiscardPile().get(season).clear();
    Collections.shuffle(activePlayer().getDrawPile().get(season));
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
    return draw(state.getTime().season(), MAX_HAND_SIZE);
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
    requireNotSubmitted("placeCard");

    if (card == null) {
      throw new IllegalArgumentException("card cannot be null");
    }

    if (!activePlayer().getHand().contains(card)) {
      throw new IllegalStateException("card is not in hand");
    }

    List<Card> table = activePlayer().getTable(season());

    // if there is no card to upgrade and no space on the table, you cannot play this card, reject.
    if (!activePlayer().tableContains(season(), card) && table.size() >= MAX_TABLE_SIZE) {
      throw new IllegalStateException(state.getTime().season() + " table is full");
    }

    // if you cannot afford the production cost, reject
    if (activePlayer().getResources().production() < card.productionRequirement()) {
      throw new IllegalStateException(
          "not enough production to place card: need "
              + (int) card.productionRequirement()
              + ", have "
              + (int) activePlayer().getResources().production());
    }

    // Pay the production cost and remove the card from the hand.
    activePlayer().addProduction(0.0 - card.productionRequirement());
    activePlayer().getHand().remove(card);

    // If a matching card is already on the table, merge the hand card into it so that
    // engine-boosted modifiers are preserved: Farm(14) + Farm(10) → Farm II(24).
    // Otherwise just add it to the table as a new slot.
    if (activePlayer().tableContains(season(), card)) {
      Card tableCard = activePlayer().findTableCard(season(), card.name());
      activePlayer().replaceTableCard(season(), tableCard, tableCard.mergeWith(card));
    } else {
      activePlayer().addToTable(season(), card);
    }
  }

  /**
   * Places the hand card whose {@link Card#name()} matches {@code name} (case-sensitive). Delegates
   * to {@link #placeCard(Card)} after locating the card.
   *
   * @throws IllegalStateException if no hand card matches the given name
   */
  public synchronized void placeCardByName(String name) {
    Card card =
        activePlayer().getHand().stream()
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
    List<Card> table = activePlayer().getTable(season());
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
    requireNotSubmitted("reorderTable");

    List<Card> table = activePlayer().getTable(season());

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
    requireNotSubmitted("demolishCard");

    List<Card> table = activePlayer().getTable(season());

    if (slot < 0 || slot >= table.size()) {
      throw new IllegalStateException("invalid slot: " + slot);
    }

    Card card = table.get(slot);
    double cost = card.productionRequirement() * DEMOLISH_COST_RATE;

    // if you cannot afford the production cost, reject.
    if (activePlayer().getResources().production() < cost) {
      throw new IllegalStateException(
          "not enough production to demolish: need "
              + cost
              + ", have "
              + activePlayer().getResources().production());
    }

    // else pay and demolish card
    activePlayer().addProduction(0.0 - cost);
    activePlayer().removeFromTableAt(season(), slot);
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
   *   <li>The Great Work's {@link Card#season()} matches the current season.
   *   <li>The Great Work has not already been drafted (sitting in a draw/discard pile or hand),
   *       commissioned, or completed — each Great Work may only ever exist once.
   *   <li>The current season's table has at least one open slot.
   *   <li>The player has enough production to cover the commission cost (the under-construction
   *       card's {@link Card#productionRequirement()}).
   *   <li>If the under-construction card's own effects include a {@link ResourceTarget#GOLD} or
   *       {@link ResourceTarget#STRENGTH} cost, that resource must already be unlocked — the same
   *       rule {@code state.areRequiredResourcesUnlocked} applies to the ADD-phase offer pool.
   *       Great Works with no such cost (e.g. THE_GREAT_RESERVOIR) are always commissionable.
   * </ul>
   *
   * @param name stable name of the Great Work (e.g. {@code "TREASURY"})
   * @throws IllegalStateException on any precondition violation
   * @throws IllegalArgumentException if no Great Work with {@code name} exists in the catalog
   */
  public synchronized void commissionGreatWork(String name) {
    requirePhase(Phase.PLAY, "commissionGreatWork");
    requireNotSubmitted("commissionGreatWork");

    Card gw = cardCatalog.getByName(name);
    if (gw == null || !gw.isGreatWork()) {
      throw new IllegalArgumentException("no great work with name: " + name);
    }

    if (gw.season() != state.getTime().season()) {
      throw new IllegalStateException(
          "cannot commission "
              + name
              + " during "
              + state.getTime().season()
              + " — it belongs on the "
              + gw.season()
              + " table");
    }

    // Already drafted (sitting in a draw pile, discard pile, or hand), already commissioned
    // (construction card on a table), or already completed (completed card on a table). A Great
    // Work may only ever exist once, regardless of which path put it there.
    if (greatWorkAlreadyClaimed(gw)) {
      throw new IllegalStateException(
          "great work already drafted, commissioned, or completed: " + name);
    }

    List<Card> targetTable = activePlayer().getBoard().get(gw.season());

    if (targetTable.size() >= MAX_TABLE_SIZE) {
      throw new IllegalStateException(gw.season() + " table is full");
    }

    // A Great Work can only be commissioned once any resource its own construction cost
    // references (GOLD/STRENGTH) is unlocked — the same rule that gates the ADD-phase offer pool.
    // Great Works with no such cost (e.g. THE_GREAT_RESERVOIR) are always commissionable.
    if (!activePlayer().areRequiredResourcesUnlocked(gw)) {
      throw new IllegalStateException(
          "commissioning " + name + " requires its locked resource(s) to be unlocked first");
    }

    // Pay the one-time commission cost.
    double cost = gw.productionRequirement();
    if (activePlayer().getResources().production() < cost) {
      throw new IllegalStateException(
          "not enough production to commission "
              + name
              + ": need "
              + (int) cost
              + ", have "
              + (int) activePlayer().getResources().production());
    }
    activePlayer().addProduction(0.0 - cost);

    targetTable.add(gw);
  }

  // ----- Great Work helpers -----

  /**
   * Returns all great work cards from the catalog whose {@link Card#season()} matches the given
   * season and that have not yet been commissioned. Used by clients to populate the Great Works
   * commissioning menu.
   *
   * @param season the season whose available Great Works to list
   * @return Great Works available for commissioning in {@code season}
   */
  /**
   * Returns Great Work construction cards available for commissioning on {@code season}'s table —
   * i.e. cards that haven't already been drafted, commissioned, or completed anywhere in the game.
   * Each Great Work may only ever exist once.
   */
  public synchronized List<Card> availableGreatWorks(Season season) {
    return cardCatalog.greatWorksForSeason(season).stream()
        .filter(gw -> !greatWorkAlreadyClaimed(gw))
        .toList();
  }

  // ----- COUNCIL_ADD: advisor offer / hire / skip -----

  /**
   * Offers up to {@value #COUNCIL_OFFER_SIZE} random advisors drawn from {@link
   * AdvisorCatalog#ALL}, excluding any already seated and any whose {@link AdvisorGate} is not yet
   * open (see {@link #isAdvisorGateOpen(Advisor)}).
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#COUNCIL_ADD}
   */
  public synchronized List<Advisor> offerCouncilChoices() {
    requirePhase(Phase.COUNCIL_ADD, "offerCouncilChoices");
    List<Advisor> eligible =
        AdvisorCatalog.ALL.stream()
            .filter(a -> activePlayer().findSeatedAdvisor(a.id()) == null)
            .filter(this::isAdvisorGateOpen)
            .toList();
    List<Advisor> choices = pickRandomAdvisors(eligible, COUNCIL_OFFER_SIZE);
    activePlayer().setCouncilOfferChoices(choices);
    return choices;
  }

  /**
   * Hires {@code advisor} onto the council: deducts {@link Advisor#hiringCost()} gold, seats a new
   * {@link SeatedAdvisor}, then advances the phase to {@link Phase#COUNCIL_MEETING} and offers that
   * year's single rotating project proposal.
   *
   * @throws IllegalArgumentException if {@code advisor} is {@code null}
   * @throws IllegalStateException if the current phase is not {@link Phase#COUNCIL_ADD}, the
   *     council is full, {@code advisor} was not part of this round's offer, {@code advisor} is
   *     already seated, or gold is insufficient
   */
  public synchronized void hireAdvisor(Advisor advisor) {
    requirePhase(Phase.COUNCIL_ADD, "hireAdvisor");
    requireNotSubmitted("hireAdvisor");
    if (advisor == null) {
      throw new IllegalArgumentException("advisor cannot be null");
    }
    if (activePlayer().getCouncil().size() >= MAX_COUNCIL_SIZE) {
      throw new IllegalStateException("council is full");
    }
    if (!activePlayer().getCouncilOfferChoices().contains(advisor)) {
      throw new IllegalStateException("advisor was not offered this round: " + advisor.id());
    }
    if (activePlayer().findSeatedAdvisor(advisor.id()) != null) {
      throw new IllegalStateException("advisor is already seated: " + advisor.id());
    }
    if (activePlayer().getResources().gold() < advisor.hiringCost()) {
      throw new IllegalStateException(
          "not enough gold to hire "
              + advisor.displayName()
              + ": need "
              + advisor.hiringCost()
              + ", have "
              + (int) activePlayer().getResources().gold());
    }
    activePlayer().addGold(0.0 - advisor.hiringCost());
    activePlayer()
        .addToCouncil(new SeatedAdvisor(advisor.id(), advisor.baseCard(), state.getTime().year()));
    clearCouncilAddState();
    submitActiveSeat();
  }

  /**
   * Hires the advisor whose {@link Advisor#id()} matches {@code advisorId}. Delegates to {@link
   * #hireAdvisor(Advisor)} after looking it up in {@link AdvisorCatalog}.
   *
   * @throws IllegalStateException if no advisor with {@code advisorId} exists in the catalog
   */
  public synchronized void hireAdvisorById(String advisorId) {
    Advisor advisor = AdvisorCatalog.byId(advisorId);
    if (advisor == null) {
      throw new IllegalStateException("no advisor with id: " + advisorId);
    }
    hireAdvisor(advisor);
  }

  /**
   * Declines to hire anyone this year, advancing the phase to {@link Phase#COUNCIL_MEETING} and
   * offering that year's single rotating project proposal.
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#COUNCIL_ADD}
   */
  public synchronized void skipCouncilAdd() {
    requirePhase(Phase.COUNCIL_ADD, "skipCouncilAdd");
    requireNotSubmitted("skipCouncilAdd");
    clearCouncilAddState();
    submitActiveSeat();
  }

  private void clearCouncilAddState() {
    activePlayer().setCouncilOfferChoices(List.of());
  }

  /**
   * Dismisses the seated advisor identified by {@code advisorId} from the council ("let them go"),
   * freeing their seat. Charges {@link #ADVISOR_DISMISSAL_COST_RATE} × the advisor's original
   * {@link Advisor#hiringCost()} in gold, mirroring {@link #demolishCard(int)}'s production-cost
   * convention for removing a table card.
   *
   * <p>Available during either council sub-phase, since the roster panel is visible throughout the
   * whole once-a-year council turn. If the dismissed advisor was the proposing advisor for the
   * current {@link Phase#COUNCIL_MEETING} proposal, a fresh proposal is offered immediately so the
   * meeting screen never shows a proposal from someone no longer seated.
   *
   * @throws IllegalStateException if the current phase is neither {@link Phase#COUNCIL_ADD} nor
   *     {@link Phase#COUNCIL_MEETING}, if no advisor with {@code advisorId} is currently seated, or
   *     if the player cannot afford the dismissal cost
   */
  public synchronized void dismissAdvisor(String advisorId) {
    requireCouncilPhase("dismissAdvisor");
    requireNotSubmitted("dismissAdvisor");
    SeatedAdvisor seated = activePlayer().findSeatedAdvisor(advisorId);
    if (seated == null) {
      throw new IllegalStateException("advisor is not seated: " + advisorId);
    }
    Advisor advisor = AdvisorCatalog.byId(advisorId);
    double cost = (advisor == null ? 0 : advisor.hiringCost()) * ADVISOR_DISMISSAL_COST_RATE;
    if (activePlayer().getResources().gold() < cost) {
      throw new IllegalStateException(
          "not enough gold to let go "
              + (advisor == null ? advisorId : advisor.displayName())
              + ": need "
              + cost
              + ", have "
              + (int) activePlayer().getResources().gold());
    }
    activePlayer().addGold(0.0 - cost);
    activePlayer().removeFromCouncil(advisorId);
    CouncilProposal proposal = activePlayer().getCurrentProposal();
    if (state.getTime().phase() == Phase.COUNCIL_MEETING
        && proposal != null
        && proposal.advisorId().equals(advisorId)) {
      offerCouncilProposal();
    }
  }

  /**
   * Returns {@code true} if {@code advisor}'s {@link AdvisorGate} is currently satisfied. {@link
   * AdvisorGate#ANY_GREAT_WORK_COMMISSIONED} / {@link AdvisorGate#GREAT_ROAD_COMMISSIONED} are
   * evaluated by scanning the board directly (see {@link #isGreatWorkCommissioned(Card)}) rather
   * than {@code GameState#isCardInPlay}, because the starting deck seeds TREASURY_CONSTRUCTION and
   * BARRACKS_CONSTRUCTION directly into draw piles — sitting in a draw pile is not "commissioned".
   */
  private boolean isAdvisorGateOpen(Advisor advisor) {
    return switch (advisor.gate()) {
      case NONE -> true;
      case BARRACKS_COMPLETED -> activePlayer().getResources().isUnlocked(ResourceTarget.STRENGTH);
      case ANY_GREAT_WORK_COMMISSIONED ->
          CardCatalog.ALL_GREAT_WORKS.stream().anyMatch(this::isGreatWorkCommissioned);
      case GREAT_ROAD_COMMISSIONED ->
          isGreatWorkCommissioned(CardCatalog.THE_GREAT_ROAD_CONSTRUCTION);
    };
  }

  /**
   * Returns {@code true} if {@code constructionCard} (or its completed counterpart) is currently
   * sitting on any season's table — i.e. construction has at least started, whether or not it has
   * since finished.
   */
  private boolean isGreatWorkCommissioned(Card constructionCard) {
    if (findCardOnBoard(constructionCard.name()) != null) return true;
    Card completed = constructionCard.completedCard();
    return completed != null && findCardOnBoard(completed.name()) != null;
  }

  /** Returns {@code count} distinct advisors chosen uniformly at random from {@code source}. */
  private List<Advisor> pickRandomAdvisors(List<Advisor> source, int count) {
    List<Advisor> pool = new ArrayList<>(source);
    Collections.shuffle(pool, rng);
    return List.copyOf(pool.subList(0, Math.min(count, pool.size())));
  }

  // ----- COUNCIL_MEETING: project proposal, accept / pass -----

  /**
   * Chooses one seated advisor with something left to propose — the one with the smallest {@link
   * SeatedAdvisor#getLastProposalYear()} (ties broken by council order; freshly hired advisors
   * default to {@code -1} so they are always proposed first) — and stores their single available
   * project as {@link GameState#getCurrentProposal()}. If no seated advisor currently has anything
   * available, the current proposal is set to {@code null}.
   *
   * <p>When an advisor has both an available Reform and an available Decree, the Decree takes
   * priority (a simple, deterministic tie-break; the design doc does not specify an order).
   */
  private void offerCouncilProposal() {
    SeatedAdvisor chosen = null;
    Advisor chosenAdvisor = null;
    for (SeatedAdvisor seated : activePlayer().getCouncil()) {
      Advisor advisor = AdvisorCatalog.byId(seated.getAdvisorId());
      if (!hasAnyProposalAvailable(seated, advisor)) continue;
      if (chosen == null || seated.getLastProposalYear() < chosen.getLastProposalYear()) {
        chosen = seated;
        chosenAdvisor = advisor;
      }
    }
    if (chosen == null) {
      activePlayer().setCurrentProposal(null);
      return;
    }
    if (isDecreeAvailable(chosen, chosenAdvisor.decree())) {
      activePlayer()
          .setCurrentProposal(
              new CouncilProposal(
                  chosen.getAdvisorId(), ProposalKind.DECREE, chosenAdvisor.decree().name()));
    } else {
      activePlayer()
          .setCurrentProposal(
              new CouncilProposal(
                  chosen.getAdvisorId(), ProposalKind.REFORM, chosenAdvisor.reform().name()));
    }
  }

  /**
   * Accepts the current rotating proposal: applies its Reform or Decree, marks the proposing
   * advisor's rotation timestamp, clears the proposal, then advances the phase to {@link
   * Phase#ADD}.
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#COUNCIL_MEETING}, if
   *     there is no current proposal, or if the proposing advisor is no longer seated
   */
  public synchronized void acceptCouncilProposal() {
    requirePhase(Phase.COUNCIL_MEETING, "acceptCouncilProposal");
    requireNotSubmitted("acceptCouncilProposal");
    CouncilProposal proposal = activePlayer().getCurrentProposal();
    if (proposal == null) {
      throw new IllegalStateException("no current proposal to accept");
    }
    SeatedAdvisor seated = activePlayer().findSeatedAdvisor(proposal.advisorId());
    if (seated == null) {
      throw new IllegalStateException(
          "proposing advisor is no longer seated: " + proposal.advisorId());
    }
    Advisor advisor = AdvisorCatalog.byId(seated.getAdvisorId());
    if (proposal.kind() == ProposalKind.DECREE) {
      applyDecree(seated, advisor.decree());
      seated.setDecreeTaken(true);
    } else {
      applyReform(seated, advisor.reform());
    }
    seated.setLastProposalYear(state.getTime().year());
    activePlayer().setCurrentProposal(null);
    submitActiveSeat();
  }

  /**
   * Passes on the current rotating proposal without applying it. The proposing advisor's rotation
   * timestamp is still updated (so the rotation moves on to a different advisor next year), then
   * the phase advances to {@link Phase#ADD}. No-ops gracefully if there is no current proposal
   * (every seated advisor was already out of projects to propose).
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#COUNCIL_MEETING}
   */
  public synchronized void passCouncilProposal() {
    requirePhase(Phase.COUNCIL_MEETING, "passCouncilProposal");
    requireNotSubmitted("passCouncilProposal");
    CouncilProposal proposal = activePlayer().getCurrentProposal();
    if (proposal != null) {
      SeatedAdvisor seated = activePlayer().findSeatedAdvisor(proposal.advisorId());
      if (seated != null) {
        seated.setLastProposalYear(state.getTime().year());
      }
    }
    activePlayer().setCurrentProposal(null);
    submitActiveSeat();
  }

  /** Returns {@code true} if {@code seated} currently has an available Reform stack or Decree. */
  private boolean hasAnyProposalAvailable(SeatedAdvisor seated, Advisor advisor) {
    return isDecreeAvailable(seated, advisor.decree())
        || isReformAvailable(seated, advisor.reform());
  }

  /** Returns {@code true} if {@code seated} has not yet taken their Decree and it is available. */
  private boolean isDecreeAvailable(SeatedAdvisor seated, Decree decree) {
    if (seated.isDecreeTaken()) return false;
    return switch (decree.type()) {
      case PASSIVE_BOOST, POPULATION_CAP_BOOST, FREE_REROLL -> true;
      case GREAT_WORK_BOOST -> findCardOnBoard(decree.targetGreatWorkName()) != null;
      case GREAT_WORK_ACCELERATE -> findAcceleratableGreatWork() != null;
    };
  }

  /** Returns {@code true} if {@code seated} has not yet exhausted this Reform's max stacks. */
  private boolean isReformAvailable(SeatedAdvisor seated, Reform reform) {
    return seated.getReformStacksTaken() < reform.maxStacks();
  }

  /**
   * Applies one stack of {@code reform} to {@code seated}: grants the benefit described by {@link
   * Reform#benefitType()}, then always drains {@link Reform#costResource()} on the advisor's own
   * card by {@link Reform#costPerStack()} (a negative magnitude), then increments the stack
   * counter.
   */
  private void applyReform(SeatedAdvisor seated, Reform reform) {
    switch (reform.benefitType()) {
      case CARD_EFFECT ->
          seated.setCard(
              seated
                  .getCard()
                  .withEffectBoostOrAdd(reform.benefitResource(), reform.benefitPerStack()));
      case FREE_REROLL -> seated.addBonusFreeRerolls((int) reform.benefitPerStack());
      case EXTRA_ADD_CANDIDATE -> seated.addBonusExtraAddCandidates((int) reform.benefitPerStack());
      case CROSS_TABLE_BOOST -> seated.addBonusCrossTableBoost(reform.benefitPerStack());
    }
    seated.setCard(
        seated.getCard().withEffectBoostOrAdd(reform.costResource(), reform.costPerStack()));
    seated.incrementReformStacksTaken();
  }

  /** Applies the one-time effect of {@code decree} to {@code seated} / the board. */
  private void applyDecree(SeatedAdvisor seated, Decree decree) {
    switch (decree.type()) {
      case PASSIVE_BOOST ->
          seated.setCard(
              seated
                  .getCard()
                  .withEffectBoostOrAdd(decree.effectResource(), decree.effectAmount()));
      case GREAT_WORK_BOOST -> {
        Card target = findCardOnBoard(decree.targetGreatWorkName());
        if (target != null) {
          replaceCardOnBoard(
              target, target.withEffectBoostOrAdd(decree.effectResource(), decree.effectAmount()));
        }
      }
      case POPULATION_CAP_BOOST -> activePlayer().addPopulationCap(decree.effectAmount());
      case GREAT_WORK_ACCELERATE -> accelerateOneGreatWork();
      case FREE_REROLL -> activePlayer().incrementPermanentFreeRerolls((int) decree.effectAmount());
    }
  }

  /**
   * Advances the first not-yet-accelerated Great Work currently under construction (anywhere on the
   * board) by one season, skipping its per-season cost entirely. Completes it (swapping in the
   * completed card and unlocking any resources it grants) if this was its last remaining period.
   * Marks the Great Work as accelerated either way, so Tomas Ferro's Decree can't be reused on the
   * same one. No-ops if no eligible Great Work exists.
   */
  private void accelerateOneGreatWork() {
    Card target = findAcceleratableGreatWork();
    if (target == null) return;

    int newPeriod = target.constructionPeriod() - 1;
    if (newPeriod <= 0) {
      Card completed = target.completedCard();
      replaceCardOnBoard(target, completed);
      activePlayer().unlock(target);
    } else {
      Card decremented = target.withConstructionPeriod(newPeriod);
      replaceCardOnBoard(target, decremented);
    }
    activePlayer().markGreatWorkAccelerated(target.name());
  }

  /**
   * Returns the first Great Work currently under construction anywhere on the board that has not
   * already received Tomas Ferro's acceleration, or {@code null} if none qualifies.
   */
  private Card findAcceleratableGreatWork() {
    for (List<Card> table : activePlayer().getBoard().values()) {
      for (Card c : table) {
        if (c.isGreatWork() && !activePlayer().getAcceleratedGreatWorkNames().contains(c.name())) {
          return c;
        }
      }
    }
    return null;
  }

  // ----- Cross-board lookup helpers (Council Decrees can target a card on any season's table)
  // -----

  /** Scans every season's table for a card named {@code name}, returning the first match found. */
  private Card findCardOnBoard(String name) {
    for (List<Card> table : activePlayer().getBoard().values()) {
      for (Card c : table) {
        if (c.name().equals(name)) return c;
      }
    }
    return null;
  }

  /**
   * Replaces {@code oldCard} with {@code newCard} on whichever season's table currently holds it.
   */
  private void replaceCardOnBoard(Card oldCard, Card newCard) {
    for (List<Card> table : activePlayer().getBoard().values()) {
      int idx = table.indexOf(oldCard);
      if (idx != -1) {
        table.set(idx, newCard);
        return;
      }
    }
  }

  // ----- Seated-advisor meta-mechanic totals (Joric / Varn / Ostlar) -----

  /** Total extra free ADD-phase rerolls granted by every seated advisor's base trait + Reforms. */
  private int seatedFreeRerollBonus() {
    int total = 0;
    for (SeatedAdvisor seated : activePlayer().getCouncil()) {
      Advisor advisor = AdvisorCatalog.byId(seated.getAdvisorId());
      total += advisor.freeRerollsWhileSeated() + seated.getBonusFreeRerolls();
    }
    return total;
  }

  /**
   * Total extra ADD-phase offer candidates granted by every seated advisor's base trait + Reforms.
   */
  private int seatedExtraAddCandidates() {
    int total = 0;
    for (SeatedAdvisor seated : activePlayer().getCouncil()) {
      Advisor advisor = AdvisorCatalog.byId(seated.getAdvisorId());
      total += advisor.extraAddCandidatesWhileSeated() + seated.getBonusExtraAddCandidates();
    }
    return total;
  }

  /** Total cross-table Production boost granted by every seated advisor's base trait + Reforms. */
  private double seatedCrossTableBoost() {
    double total = 0;
    for (SeatedAdvisor seated : activePlayer().getCouncil()) {
      Advisor advisor = AdvisorCatalog.byId(seated.getAdvisorId());
      total += advisor.crossTableBoostAmount() + seated.getBonusCrossTableBoost();
    }
    return total;
  }
}
