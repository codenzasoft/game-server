package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Owns all rule-based mutations of a {@link GameState}: phase/season advancement, table mutations,
 * the per-season card-trigger pass, and draw/discard/hand pile mutations. {@code GameState} is a
 * passive data carrier; this engine is the only place that should mutate it.
 */
public class GameEngine {

  public static final int MAX_TABLE_SIZE = 5;
  public static final int REROLL_COST_STEP = 5;
  static final int ADD_OFFER_SIZE = 3;

  private final GameState state;
  private final CardCatalog catalog;
  private final Random random;

  public GameEngine(GameState state) {
    this(state, CardCatalog.defaultCatalog(), new Random());
  }

  public GameEngine(GameState state, CardCatalog catalog) {
    this(state, catalog, new Random());
  }

  /** Package-visible constructor for testing with a deterministic {@link Random}. */
  GameEngine(GameState state, CardCatalog catalog, Random random) {
    if (state == null) {
      throw new IllegalArgumentException("state cannot be null");
    }
    this.state = state;
    this.catalog = catalog;
    this.random = random;
  }

  public GameState getState() {
    return state;
  }

  // ----- Phase / season progression -----

  public synchronized void advancePhase() {
    // if play phase ends, move to the next season
    if (state.getCurPhase() == Phase.PLAY) {
      advanceSeason();
    }

    state.setCurPhase(state.getCurPhase().next());
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

  // ----- ADD phase: card offer, pick, reroll, skip -----

  /**
   * Returns three uniformly-random cards from the full catalog as the player's choices for this ADD
   * phase. Resets the reroll counter so costs start fresh at {@value #REROLL_COST_STEP} production.
   *
   * @throws IllegalStateException if the current phase is not {@link Phase#ADD}
   */
  public synchronized List<Card> offerAddChoices() {
    requirePhase(Phase.ADD, "offerAddChoices");
    state.setRerollCount(0);
    List<Card> choices = pickRandom(catalog.forSeason(state.getCurSeason()), ADD_OFFER_SIZE);
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
   * Replaces the currently offered cards with a fresh set, deducting an escalating production cost.
   * The cost for the <em>n</em>-th reroll (1-indexed) is {@code n * }{@value #REROLL_COST_STEP}.
   *
   * @return the new set of three offered cards
   * @throws IllegalStateException if the current phase is not {@link Phase#ADD}, or if the player
   *     cannot afford the reroll
   */
  public synchronized List<Card> rerollAddChoices() {
    requirePhase(Phase.ADD, "rerollAddChoices");
    int cost = (state.getRerollCount() + 1) * REROLL_COST_STEP;
    if (state.getProduction() < cost) {
      throw new IllegalStateException(
          "not enough production to reroll: need "
              + cost
              + ", have "
              + (int) state.getProduction());
    }
    state.setProduction(state.getProduction() - cost);
    state.setRerollCount(state.getRerollCount() + 1);
    List<Card> choices = pickRandom(catalog.forSeason(state.getCurSeason()), ADD_OFFER_SIZE);
    state.setAddChoices(choices);
    return choices;
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
    Collections.shuffle(pool, random);
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
