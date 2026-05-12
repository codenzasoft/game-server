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
 *       #chooseEventOption(EventOption)}, which applies its effects and advances the phase to
 *       {@link Phase#PLAY}.
 * </ol>
 *
 * <p><b>Winter scaling (KH-6):</b> when the current season is {@link Season#WINTER}, each effect's
 * numeric modifier is multiplied by the current year before it is applied, so winters grow
 * progressively harsher as the run extends.
 */
public class GameEngine {

  public static final int MAX_TABLE_SIZE = 5;
  public static final int REROLL_COST_STEP = 10;
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
    // if play phase ends, move to the next season

    switch (state.getCurPhase()) {
      case ADD -> rollEvent();
      case RANDOM_EVENT -> {}
      case PLAY -> advanceSeason();
    }

    state.setCurPhase(state.getCurPhase().next());
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
          case POPULATION -> state.setPopulation(scaled.apply(state.getPopulation()));
          case PRODUCTION -> state.setProduction(scaled.apply(state.getProduction()));
          case FOOD -> state.setFood(scaled.apply(state.getFood()));
        }
      }
      // CardTarget effects are not yet handled here — same deferral as triggerCurrentSeasonTable
    }

    state.setCurPhase(Phase.PLAY);
  }

  /**
   * Returns the effect unchanged for non-winter seasons; for {@link Season#WINTER} returns a new
   * {@link Effect} whose modifier is scaled by the current year (KH-6 winter scaling rule).
   */
  private Effect scaleForSeason(Effect effect) {
    if (state.getCurSeason() != Season.WINTER) {
      return effect;
    }
    return new Effect(
        effect.target(),
        effect.modifier() * state.getYear(),
        effect.valueType(),
        effect.frequencyType());
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

    // Walk every effect on every card on the current season's table. For now, only RECURRING
    // effects targeting a global resource (population / production / food) fire here. SELF and
    // CARD targets — and SINGLE-frequency effects — are handled by the end-of-PLAY resolver
    // landing in a later PR.
    for (Card card : table) {
      for (Effect effect : card.effects()) {

        if (effect.frequencyType() == FrequencyType.RECURRING) {

          if (effect.target() instanceof ResourceTarget rt) {
            switch (rt) {
              case POPULATION -> state.setPopulation(effect.apply(state.getPopulation()));
              case PRODUCTION -> state.setProduction(effect.apply(state.getProduction()));
              case FOOD -> state.setFood(effect.apply(state.getFood()));
            }
          }
        }
      }
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

  // move hand to discard pile, clear hand.
  public synchronized void discardHand(Season season) {
    if (season == null) {
      throw new IllegalStateException("season cannot be null");
    }

    state.getDiscardPile().get(season).addAll(state.getHand());
    state.getHand().clear();
  }
}
