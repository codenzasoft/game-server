package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Immutable per-player kingdom state: the per-season card piles ({@link SeasonState}), the current
 * {@link Deck} hand, {@link ResourceState}, ADD-phase offer state, and Council/Advisor state. All
 * mutation is expressed as {@code with*} / delegate methods that return a new {@code PlayerState};
 * the rule logic that drives those calls lives in {@link GameEngine}.
 *
 * <p>Because a player no longer owns the shared clock, every table operation takes the {@link
 * Season} as an explicit parameter; the match resolves the current season from its {@link
 * TimeState} before delegating.
 *
 * <p>Read accessors are exposed as {@code getX()} methods (rather than the record's canonical
 * {@code x()} accessors) to keep the large number of existing call sites unchanged.
 */
public record PlayerState(
    Map<Season, SeasonState> seasons,
    Deck hand,
    ResourceState resources,
    List<Card> addChoices,
    int rerollCount,
    List<ResolutionStep> resolutionLog,
    List<SeatedAdvisor> council,
    List<Advisor> councilOfferChoices,
    CouncilProposal currentProposal,
    int permanentFreeRerolls,
    Set<String> acceleratedGreatWorkNames,
    boolean forfeited) {

  /** Compact constructor — normalise nulls and defensively copy every collection. */
  public PlayerState {
    seasons = seasons == null ? new EnumMap<>(Season.class) : Map.copyOf(seasons);
    hand = hand == null ? Deck.empty() : hand;
    addChoices = addChoices == null ? List.of() : List.copyOf(addChoices);
    resolutionLog = resolutionLog == null ? List.of() : List.copyOf(resolutionLog);
    council = council == null ? List.of() : List.copyOf(council);
    councilOfferChoices =
        councilOfferChoices == null ? List.of() : List.copyOf(councilOfferChoices);
    acceleratedGreatWorkNames =
        acceleratedGreatWorkNames == null ? Set.of() : Set.copyOf(acceleratedGreatWorkNames);
  }

  /** Creates a fresh player kingdom: empty season tables, starting population/production. */
  public static PlayerState newPlayer() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Seeds a {@link Builder} pre-populated with this kingdom's current state (basis for {@code
   * with*}).
   */
  public Builder toBuilder() {
    return new Builder()
        .seasons(seasons)
        .hand(hand)
        .resources(resources)
        .addChoices(addChoices)
        .rerollCount(rerollCount)
        .resolutionLog(resolutionLog)
        .council(council)
        .councilOfferChoices(councilOfferChoices)
        .currentProposal(currentProposal)
        .permanentFreeRerolls(permanentFreeRerolls)
        .acceleratedGreatWorkNames(acceleratedGreatWorkNames)
        .forfeited(forfeited);
  }

  // ===== Read accessors =====

  public ResourceState getResources() {
    return resources;
  }

  /** The {@link SeasonState} for {@code season}. */
  public SeasonState getSeason(Season season) {
    return seasons.get(season);
  }

  /** Board piles by season, as {@code List<Card>} (wire-compatible shape for the views). */
  public Map<Season, List<Card>> getBoard() {
    Map<Season, List<Card>> m = new EnumMap<>(Season.class);
    seasons.forEach((s, ss) -> m.put(s, ss.board().asList()));
    return m;
  }

  /** Convenience accessor for the table (board) belonging to {@code season}. */
  public List<Card> getTable(Season season) {
    return seasons.get(season).board().asList();
  }

  /**
   * Returns {@code true} if {@code season}'s table contains a card whose name matches {@code
   * card}'s name (name match, not full record equality, so an engine-boosted table card still
   * counts as the upgrade target).
   */
  public boolean tableContains(Season season, Card card) {
    return seasons.get(season).board().containsName(card.name());
  }

  /**
   * Returns the first table card on {@code season} whose name equals {@code name}, or {@code null}.
   */
  public Card findTableCard(Season season, String name) {
    return seasons.get(season).board().findByName(name).orElse(null);
  }

  /**
   * Returns the table card on {@code season} that {@code handCard} would upgrade, or {@code null}.
   * Recurring cards upgrade by identity (any level); single-use cards additionally require the same
   * level. See the original contract for detail.
   */
  public Card findUpgradeTarget(Season season, Card handCard) {
    if (handCard.frequencyType() == FrequencyType.RECURRING) {
      return seasons.get(season).board().findByName(handCard.name()).orElse(null);
    }
    return seasons.get(season).board().stream()
        .filter(c -> c.name().equals(handCard.name()) && c.level() == handCard.level())
        .findFirst()
        .orElse(null);
  }

  public Map<Season, List<Card>> getDiscardPile() {
    Map<Season, List<Card>> m = new EnumMap<>(Season.class);
    seasons.forEach((s, ss) -> m.put(s, ss.discardPile().asList()));
    return m;
  }

  public Map<Season, List<Card>> getDrawPile() {
    Map<Season, List<Card>> m = new EnumMap<>(Season.class);
    seasons.forEach((s, ss) -> m.put(s, ss.drawPile().asList()));
    return m;
  }

  public List<Card> getHand() {
    return hand.asList();
  }

  /**
   * Returns {@code true} if a card named {@code name} exists anywhere in this kingdom — the hand or
   * any season's board, draw pile, or discard pile.
   */
  public boolean isCardInPlay(String name) {
    if (hand.containsName(name)) {
      return true;
    }
    for (SeasonState ss : seasons.values()) {
      if (ss.board().containsName(name)
          || ss.drawPile().containsName(name)
          || ss.discardPile().containsName(name)) {
        return true;
      }
    }
    return false;
  }

  /** Returns {@code true} if {@code card} (matched by name) exists anywhere in the kingdom. */
  public boolean isCardInPlay(Card card) {
    return isCardInPlay(card.name());
  }

  public List<Card> getAddChoices() {
    return addChoices;
  }

  public int getRerollCount() {
    return rerollCount;
  }

  public boolean isGameOver() {
    return forfeited || resources.population() < 1.0;
  }

  /** {@code true} once this kingdom's player has given up (the "gives up → forfeits" rule). */
  public boolean isForfeited() {
    return forfeited;
  }

  /** Returns staffing for {@code season}'s table. */
  public List<CardStaffing> getTableStaffing(Season season) {
    return CardStaffing.computeStaffing(getTable(season), resources);
  }

  /** Per-card resolution log from the most recent PLAY end-and-run; empty between seasons. */
  public List<ResolutionStep> getResolutionLog() {
    return resolutionLog;
  }

  /**
   * Returns {@code true} if every {@link ResourceTarget} referenced by {@code card}'s effects is
   * currently unlocked.
   */
  public boolean areRequiredResourcesUnlocked(Card card) {
    return resources.unlocked().containsAll(card.requiredResources());
  }

  /**
   * Returns {@code true} if applying every {@link ResourceTarget} effect in {@code effects} would
   * leave production, food, gold, and strength all {@code >= 0}.
   */
  public boolean canAfford(List<Effect> effects) {
    double simProd = resources.production();
    double simFood = resources.food();
    double simGold = resources.gold();
    double simStrength = resources.strength();

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
   * Returns {@code true} once every win-condition Great Work has completed — a completed
   * (non-under-construction) victory card exists for each seasonal win condition.
   */
  public boolean isVictoryComplete() {
    long completedVictoryCards =
        seasons.values().stream()
            .flatMap(ss -> ss.board().stream())
            .filter(c -> c.victory() && !c.isGreatWork())
            .count();
    long totalVictoryCards = CardCatalog.ALL_GREAT_WORKS.stream().filter(Card::victory).count();
    return completedVictoryCards >= totalVictoryCards;
  }

  /** Alias for {@link #isVictoryComplete()} kept for existing call sites. */
  public boolean isGameWon() {
    return isVictoryComplete();
  }

  /** Advisors currently seated on the council; never more than {@code MAX_COUNCIL_SIZE} (3). */
  public List<SeatedAdvisor> getCouncil() {
    return council;
  }

  /** Returns the seated advisor whose id equals {@code advisorId}, or {@code null}. */
  public SeatedAdvisor findSeatedAdvisor(String advisorId) {
    return council.stream()
        .filter(s -> s.getAdvisorId().equals(advisorId))
        .findFirst()
        .orElse(null);
  }

  /** Candidate advisors offered during the current COUNCIL_ADD phase; empty outside it. */
  public List<Advisor> getCouncilOfferChoices() {
    return councilOfferChoices;
  }

  /** The rotating Reform/Decree proposal for the current COUNCIL_MEETING phase, or {@code null}. */
  public CouncilProposal getCurrentProposal() {
    return currentProposal;
  }

  /** Cumulative permanent free-reroll count granted by Decrees. */
  public int getPermanentFreeRerolls() {
    return permanentFreeRerolls;
  }

  /** Names of Great Works that have already received "Expedite Construction". */
  public Set<String> getAcceleratedGreatWorkNames() {
    return acceleratedGreatWorkNames;
  }

  /** Cards positioned after {@code card} on {@code season}'s board (empty if absent or last). */
  public List<Card> getCardsOnBoardAfter(final Season season, final Card card) {
    return seasons.get(season).board().subListAfter(card);
  }

  // ===== Mutators (return a new PlayerState) =====

  public PlayerState withSeason(Season season, SeasonState ss) {
    Map<Season, SeasonState> next = new EnumMap<>(seasons);
    next.put(season, ss);
    return toBuilder().seasons(next).build();
  }

  public PlayerState withHand(Deck hand) {
    return toBuilder().hand(hand).build();
  }

  public PlayerState withResources(ResourceState resources) {
    return toBuilder().resources(resources).build();
  }

  public PlayerState withAddChoices(List<Card> addChoices) {
    return toBuilder().addChoices(addChoices).build();
  }

  public PlayerState withRerollCount(int rerollCount) {
    return toBuilder().rerollCount(rerollCount).build();
  }

  public PlayerState withResolutionLog(List<ResolutionStep> log) {
    return toBuilder().resolutionLog(log).build();
  }

  public PlayerState withForfeited(boolean forfeited) {
    return toBuilder().forfeited(forfeited).build();
  }

  public PlayerState withCouncil(List<SeatedAdvisor> council) {
    return toBuilder().council(council).build();
  }

  public PlayerState withCouncilOfferChoices(List<Advisor> councilOfferChoices) {
    return toBuilder().councilOfferChoices(councilOfferChoices).build();
  }

  public PlayerState withCurrentProposal(CouncilProposal currentProposal) {
    return toBuilder().currentProposal(currentProposal).build();
  }

  public PlayerState withPermanentFreeRerolls(int permanentFreeRerolls) {
    return toBuilder().permanentFreeRerolls(permanentFreeRerolls).build();
  }

  // ----- Table shortcuts (same names as the old mutators) -----

  public PlayerState addToTable(Season season, Card card) {
    return withSeason(season, getSeason(season).addToBoard(card));
  }

  public PlayerState removeFromTable(Season season, Card card) {
    return withSeason(season, getSeason(season).removeFromBoard(card));
  }

  public PlayerState removeFromTableAt(Season season, int slot) {
    return withSeason(season, getSeason(season).removeFromBoardAt(slot));
  }

  public PlayerState replaceTableCard(Season season, Card oldCard, Card newCard) {
    return withSeason(season, getSeason(season).replaceOnBoard(oldCard, newCard));
  }

  public PlayerState discardFromTable(Season season, Card card) {
    return withSeason(season, getSeason(season).discardFromBoard(card));
  }

  /** Moves every card in the hand to {@code season}'s discard pile and clears the hand. */
  public PlayerState discardHand(Season season) {
    SeasonState discarded = getSeason(season).discardHand(hand);
    return withHand(Deck.empty()).withSeason(season, discarded);
  }

  // ----- Resource shortcuts -----

  /** Permanently unlocks every resource {@code card} unlocks (e.g. on Great Work completion). */
  public PlayerState unlock(Card card) {
    return withResources(resources.unlock(card));
  }

  public PlayerState apply(Effect effect) {
    return withResources(resources.apply(effect));
  }

  public PlayerState consumeFood(double rate) {
    return withResources(resources.consumeFood(rate));
  }

  public PlayerState addProduction(double value) {
    return withResources(resources.addProduction(value));
  }

  public PlayerState addGold(double value) {
    return withResources(resources.addGold(value));
  }

  public PlayerState addPopulationCap(double value) {
    return withResources(resources.addPopulationCap(value));
  }

  // ----- Council shortcuts -----

  /** Seats {@code seated} onto the council. Caller is responsible for the seat-count check. */
  public PlayerState addToCouncil(SeatedAdvisor seated) {
    List<SeatedAdvisor> next = new ArrayList<>(council);
    next.add(seated);
    return withCouncil(next);
  }

  /** Removes the seated advisor whose id equals {@code advisorId}, if present. */
  public PlayerState removeFromCouncil(String advisorId) {
    List<SeatedAdvisor> next = new ArrayList<>(council);
    next.removeIf(s -> s.getAdvisorId().equals(advisorId));
    return withCouncil(next);
  }

  /**
   * Applies {@code fn} to the seated advisor whose id equals {@code advisorId}, replacing it with
   * the result; a no-op if no such advisor is seated.
   */
  public PlayerState updateAdvisor(String advisorId, UnaryOperator<SeatedAdvisor> fn) {
    List<SeatedAdvisor> next = new ArrayList<>(council.size());
    for (SeatedAdvisor s : council) {
      next.add(s.getAdvisorId().equals(advisorId) ? fn.apply(s) : s);
    }
    return withCouncil(next);
  }

  public PlayerState incrementPermanentFreeRerolls(int delta) {
    return withPermanentFreeRerolls(permanentFreeRerolls + delta);
  }

  /** Records that {@code greatWorkName} has received "Expedite Construction". */
  public PlayerState markGreatWorkAccelerated(String greatWorkName) {
    Set<String> next = new java.util.HashSet<>(acceleratedGreatWorkNames);
    next.add(greatWorkName);
    return toBuilder().acceleratedGreatWorkNames(next).build();
  }

  // ===== Builder =====

  public static final class Builder {
    private final Map<Season, SeasonState> seasons = new EnumMap<>(Season.class);
    private Deck hand = Deck.empty();
    private ResourceState resources; // if set directly, used as-is (the with* / toBuilder path)
    private double population = 100.0;
    private double populationCap = 100.0;
    private double production = 1000.0;
    private double food = 750.0;
    private double gold = 0.0;
    private double strength = 0.0;
    private Set<ResourceTarget> unlockedResources =
        EnumSet.of(
            ResourceTarget.POPULATION,
            ResourceTarget.PRODUCTION,
            ResourceTarget.FOOD,
            ResourceTarget.POPULATION_CAP);
    private List<Card> addChoices = new ArrayList<>();
    private int rerollCount = 0;
    private List<ResolutionStep> resolutionLog = List.of();
    private List<SeatedAdvisor> council = new ArrayList<>();
    private List<Advisor> councilOfferChoices = new ArrayList<>();
    private CouncilProposal currentProposal = null;
    private int permanentFreeRerolls = 0;
    private Set<String> acceleratedGreatWorkNames = new java.util.HashSet<>();
    private boolean forfeited = false;

    private Builder() {
      for (Season season : Season.values()) {
        seasons.put(season, SeasonState.empty());
      }
    }

    /** Sets an entire season map directly (used by {@link PlayerState#toBuilder()}). */
    public Builder seasons(Map<Season, SeasonState> seasons) {
      this.seasons.putAll(seasons);
      return this;
    }

    public Builder table(Season season, List<Card> cards) {
      seasons.put(season, seasons.get(season).withBoard(Deck.of(cards)));
      return this;
    }

    public Builder discardPile(Season season, List<Card> cards) {
      seasons.put(season, seasons.get(season).withDiscardPile(Deck.of(cards)));
      return this;
    }

    public Builder drawPile(Season season, List<Card> cards) {
      seasons.put(season, seasons.get(season).withDrawPile(Deck.of(cards)));
      return this;
    }

    public Builder hand(List<Card> cards) {
      this.hand = Deck.of(cards);
      return this;
    }

    public Builder hand(Deck hand) {
      this.hand = hand;
      return this;
    }

    public Builder resources(ResourceState resources) {
      this.resources = resources;
      return this;
    }

    public Builder population(double population) {
      this.population = population;
      return this;
    }

    public Builder populationCap(double populationCap) {
      this.populationCap = populationCap;
      return this;
    }

    public Builder production(double production) {
      this.production = production;
      return this;
    }

    public Builder food(double food) {
      this.food = food;
      return this;
    }

    public Builder addChoices(List<Card> addChoices) {
      this.addChoices = new ArrayList<>(addChoices);
      return this;
    }

    public Builder rerollCount(int rerollCount) {
      this.rerollCount = rerollCount;
      return this;
    }

    public Builder resolutionLog(List<ResolutionStep> resolutionLog) {
      this.resolutionLog = resolutionLog == null ? List.of() : new ArrayList<>(resolutionLog);
      return this;
    }

    public Builder gold(double gold) {
      this.gold = gold;
      return this;
    }

    public Builder strength(double strength) {
      this.strength = strength;
      return this;
    }

    public Builder goldUnlocked(boolean goldUnlocked) {
      if (goldUnlocked) {
        this.unlockedResources.add(ResourceTarget.GOLD);
      } else {
        this.unlockedResources.remove(ResourceTarget.GOLD);
      }
      return this;
    }

    public Builder strengthUnlocked(boolean strengthUnlocked) {
      if (strengthUnlocked) {
        this.unlockedResources.add(ResourceTarget.STRENGTH);
      } else {
        this.unlockedResources.remove(ResourceTarget.STRENGTH);
      }
      return this;
    }

    public Builder unlockedResources(Set<ResourceTarget> unlockedResources) {
      this.unlockedResources =
          unlockedResources == null
              ? EnumSet.noneOf(ResourceTarget.class)
              : EnumSet.copyOf(unlockedResources);
      return this;
    }

    public Builder council(List<SeatedAdvisor> council) {
      this.council = council == null ? new ArrayList<>() : new ArrayList<>(council);
      return this;
    }

    public Builder councilOfferChoices(List<Advisor> councilOfferChoices) {
      this.councilOfferChoices =
          councilOfferChoices == null ? new ArrayList<>() : new ArrayList<>(councilOfferChoices);
      return this;
    }

    public Builder currentProposal(CouncilProposal currentProposal) {
      this.currentProposal = currentProposal;
      return this;
    }

    public Builder permanentFreeRerolls(int permanentFreeRerolls) {
      this.permanentFreeRerolls = permanentFreeRerolls;
      return this;
    }

    public Builder acceleratedGreatWorkNames(Set<String> acceleratedGreatWorkNames) {
      this.acceleratedGreatWorkNames =
          acceleratedGreatWorkNames == null
              ? new java.util.HashSet<>()
              : new java.util.HashSet<>(acceleratedGreatWorkNames);
      return this;
    }

    public Builder forfeited(boolean forfeited) {
      this.forfeited = forfeited;
      return this;
    }

    public PlayerState build() {
      if (population < 0.0) {
        throw new IllegalStateException("population cannot be negative");
      }
      if (production < 0.0) {
        throw new IllegalStateException("production cannot be negative");
      }
      if (food < 0.0) {
        throw new IllegalStateException("food cannot be negative");
      }
      ResourceState resolved =
          resources != null
              ? resources
              : new ResourceState(
                  food, production, population, populationCap, gold, strength, unlockedResources);
      return new PlayerState(
          seasons,
          hand,
          resolved,
          addChoices,
          rerollCount,
          resolutionLog,
          council,
          councilOfferChoices,
          currentProposal,
          permanentFreeRerolls,
          acceleratedGreatWorkNames,
          forfeited);
    }
  }
}
