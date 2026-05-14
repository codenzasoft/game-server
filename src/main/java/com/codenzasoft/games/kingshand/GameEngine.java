package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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
public class GameEngine {

  public static final int MAX_TABLE_SIZE = 5;
  public static final int MAX_HAND_SIZE = 5;
  public static final int REROLL_COST_STEP = 10;
  public static final double DEMOLISH_COST_RATE = 0.20;
  static final int ADD_OFFER_SIZE = 3;

  private final GameState state;
  private final CardCatalog cardCatalog;
  private final EventCatalog eventCatalog;
  private final Random rng;

  /**
   * Convenience constructor that uses the {@link EventCatalog#defaultCatalog() default catalog} and
   * an unseeded {@link Random}. Existing call-sites remain unaffected.
   */
  public GameEngine(GameState state) {
    this(state, CardCatalog.defaultCatalog(), EventCatalog.defaultCatalog(), new Random());
  }

  /**
   * Constructor that accepts a custom card catalog (uses default event catalog and unseeded RNG).
   */
  public GameEngine(GameState state, CardCatalog cardCatalog) {
    this(state, cardCatalog, EventCatalog.defaultCatalog(), new Random());
  }

  /**
   * Constructor that accepts a custom event catalog (uses default card catalog and unseeded RNG).
   */
  public GameEngine(GameState state, EventCatalog eventCatalog) {
    this(state, CardCatalog.defaultCatalog(), eventCatalog, new Random());
  }

  /**
   * Primary constructor — all fields injected. Prefer this form in tests so that the random
   * selection is reproducible via a seeded {@link Random}.
   */
  public GameEngine(GameState state, EventCatalog eventCatalog, Random rng) {
    this(state, CardCatalog.defaultCatalog(), eventCatalog, rng);
  }

  /** Full constructor with all dependencies injected. */
  GameEngine(GameState state, CardCatalog cardCatalog, EventCatalog eventCatalog, Random rng) {
    if (state == null) {
      throw new IllegalArgumentException("state cannot be null");
    }
    if (cardCatalog == null) {
      throw new IllegalArgumentException("catalog cannot be null");
    }
    if (eventCatalog == null) {
      throw new IllegalArgumentException("catalog cannot be null");
    }
    if (rng == null) {
      throw new IllegalArgumentException("rng cannot be null");
    }
    this.state = state;
    this.cardCatalog = cardCatalog;
    this.eventCatalog = eventCatalog;
    this.rng = rng;
  }

  public GameState getState() {
    return state;
  }

  // ----- Phase / season progression -----

  public synchronized void advancePhase() {
    if (state.isGameOver()) {
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

    for (Effect effect : option.effects()) {
      if (effect.target() instanceof ResourceTarget rt) {
        Effect scaled = scaleForSeason(effect);
        switch (rt) {
          case POPULATION -> applyPopulation(scaled.apply(state.getPopulation()));
          case PRODUCTION -> state.setProduction(scaled.apply(state.getProduction()));
          case FOOD -> state.setFood(scaled.apply(state.getFood()));
        }
      }
      // CardTarget effects are not yet handled here — same deferral as triggerCurrentSeasonTable
    }

    // Only advance to PLAY if the game is still running (population may have hit zero above).
    if (!state.isGameOver()) {
      state.setCurPhase(Phase.PLAY);
    }
  }

  /**
   * Returns the effect unchanged for non-winter seasons; for {@link Season#WINTER} returns a new
   * {@link Effect} whose modifier is scaled by the current year (KH-6 winter scaling rule).
   */
  private Effect scaleForSeason(Effect effect) {
    if (state.getCurSeason() != Season.WINTER) {
      return effect;
    }
    return new Effect(effect.target(), effect.modifier() * state.getYear(), effect.valueType());
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
    // including SINGLE-frequency cards and post-fire discard — lives in {@link #endAndRun()}.
    for (Card card : table) {
      if (card.frequencyType() == FrequencyType.RECURRING) {
        for (Effect effect : card.effects()) {
          if (effect.target() instanceof ResourceTarget rt) {
            switch (rt) {
              case POPULATION -> applyPopulation(effect.apply(state.getPopulation()));
              case PRODUCTION -> state.setProduction(effect.apply(state.getProduction()));
              case FOOD -> state.setFood(effect.apply(state.getFood()));
            }
          }
        }
      }
    }
  }

  // ----- End-of-PLAY resolution -----

  /**
   * Ends the player's turn: discards every card remaining in the hand to the current season's
   * discard pile, then resolves the current season's table left-to-right.
   *
   * <p>Resolution iterates the table in slot order (index 0 first). For each card all effects fire
   * in declaration order, so cards to the right see the resource state already mutated by cards to
   * their left. After a card's effects fire:
   *
   * <ul>
   *   <li>{@link FrequencyType#SINGLE} cards are moved to the current season's discard pile
   *       (one-shot exhaustion).
   *   <li>{@link FrequencyType#RECURRING} cards stay in their table slot.
   * </ul>
   *
   * <p>Note: {@link CardTarget} effects are not yet evaluated and are silently skipped.
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#PLAY}
   */
  public synchronized void endAndRun() {
    requirePhase(Phase.PLAY, "endAndRun");

    // 1. Discard everything remaining in hand.
    state.discardHand();

    // 2. Snapshot the table order before resolution so that removals during the walk don't
    //    disturb the iteration sequence.
    List<Card> snapshot = new ArrayList<>(state.getCurrentTable());

    for (Card card : snapshot) {
      // Fire every ResourceTarget effect on this card.
      for (Effect effect : card.effects()) {
        if (effect.target() instanceof ResourceTarget rt) {
          switch (rt) {
            case POPULATION -> applyPopulation(effect.apply(state.getPopulation()));
            case PRODUCTION -> state.setProduction(effect.apply(state.getProduction()));
            case FOOD -> state.setFood(effect.apply(state.getFood()));
          }
        }
      }

      // SINGLE cards are exhausted: remove from table and send to discard.
      if (card.frequencyType() == FrequencyType.SINGLE) {
        state.discardFromTable(card);
      }
      // RECURRING cards remain in their slot — no action needed.
    }

    // 3. Seasonal food consumption and starvation.
    consumeFood();
  }

  /**
   * Applies a population change, clamping to zero and transitioning to {@link Phase#GAME_OVER} if
   * the result is zero or below. All engine-level population mutations route through this method so
   * game-over detection is never scattered.
   */
  private void applyPopulation(double value) {
    double clamped = Math.max(0.0, value);
    state.setPopulation(clamped);
    if (clamped == 0.0) {
      state.setGameOver(true);
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
      state.setProduction(state.getProduction() - cost);
    }
    state.setRerollCount(state.getRerollCount() + 1);
    List<Card> choices = pickRandom(cardCatalog.forSeason(state.getCurSeason()), ADD_OFFER_SIZE);
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
    state.setCurPhase(Phase.RANDOM_EVENT);
  }

  /**
   * Skips the ADD phase without adding a card, advancing the phase to {@link Phase#RANDOM_EVENT}.
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#ADD}
   */
  public synchronized void skipAdd() {
    requirePhase(Phase.ADD, "skipAdd");
    clearAddState();
    state.setCurPhase(Phase.RANDOM_EVENT);
  }

  // ----- ADD helpers -----

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
    return draw(state.getCurSeason(), MAX_HAND_SIZE);
  }

  /**
   * Places {@code card} from the player's hand onto the current season's table, after validating:
   *
   * <ol>
   *   <li>The card is present in the hand.
   *   <li>The table has an open slot (<{@value #MAX_TABLE_SIZE} cards) <em>or</em> the card
   *       duplicates one already on the table (upgrade path).
   *   <li>Current production ≥ {@link Card#productionRequirement()}.
   *   <li>Current population ≥ {@link Card#populationRequirement()}.
   * </ol>
   *
   * <p>On success the production cost is deducted and the card is removed from the hand. If a
   * matching card already sits on the table, every effect's modifier on that table card is doubled
   * (upgrade) rather than adding a new slot.
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
    // if you do not meet the population requirement, reject
    if (state.getPopulation() < card.populationRequirement()) {
      throw new IllegalStateException(
          "not enough population to place card: need "
              + (int) card.populationRequirement()
              + ", have "
              + (int) state.getPopulation());
    }

    // Pay the production cost and remove the card from the hand.
    state.setProduction(state.getProduction() - card.productionRequirement());
    state.getHand().remove(card);

    // if duplicate exisits, replace with one whose every effect modifier is doubled.
    // else just add it to the table as a new card.
    if (state.tableContains(card)) {
      state.replaceTableCard(card, card.amplify(2));
    } else {
      state.addToTable(card);
    }
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
    state.setProduction(state.getProduction() - cost);
    state.removeFromTableAt(slot);
  }
}
