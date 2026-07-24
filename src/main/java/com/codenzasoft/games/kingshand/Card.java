package com.codenzasoft.games.kingshand;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A playable card. Carries its play requirements ({@code staffRequirement}, {@code
 * productionRequirement}), a {@link FrequencyType} that applies to all of its effects (one-shot vs
 * every season), and an arbitrary list of {@link Effect}s.
 *
 * <p>All effects on a card share the same {@link FrequencyType}; the engine reads frequency once
 * per card during resolution rather than per-effect.
 *
 * <p>The effects list is defensively copied at construction so callers can't mutate it after the
 * fact.
 *
 * <p><b>Great Work cards</b> carry additional fields that describe their multi-season construction
 * lifecycle. A {@code constructionPeriod > 0} identifies a card as a Great Work under construction.
 * {@link #completedCard()} is the permanent card that replaces it once construction finishes. For
 * regular cards all of these fields default to their zero/null/empty values and the card simply
 * returns itself from {@link #resolvedCompletedCard()}.
 */
public record Card(
    String name,
    double staffRequirement,
    double productionRequirement,
    FrequencyType frequencyType,
    List<Effect> effects,
    /** Number of successful construction seasons required to complete; 0 for normal cards. */
    int constructionPeriod,
    /**
     * The card that permanently replaces this one when construction completes. {@code null} for
     * normal cards — {@link #resolvedCompletedCard()} returns {@code this} in that case.
     */
    Card completedCard,
    /** Resource types unlocked when this Great Work completes; empty for most cards. */
    Set<ResourceTarget> unlockedResources,
    /** {@code true} for the four seasonal win-condition Great Works. */
    boolean victory,
    /** The season this Great Work belongs to; {@code null} for normal cards. */
    Season season) {

  public Card {
    effects = effects == null ? List.of() : List.copyOf(effects);
    unlockedResources = unlockedResources == null ? Set.of() : Set.copyOf(unlockedResources);
    constructionPeriod = Math.max(0, constructionPeriod);
  }

  /**
   * Convenience constructor for regular (non-great-work) cards. All Great Work fields default to
   * their zero/null/empty values.
   */
  public Card(
      String name,
      double staffRequirement,
      double productionRequirement,
      FrequencyType frequencyType,
      List<Effect> effects) {
    this(
        name,
        staffRequirement,
        productionRequirement,
        frequencyType,
        effects,
        0,
        null,
        Set.of(),
        false,
        null);
  }

  /** Returns {@code true} if this is a Great Work under-construction card. */
  public boolean isGreatWork() {
    return constructionPeriod > 0;
  }

  /** Returns {@code true} if every effect on this card targets another card (engine-builder). */
  public boolean isEngineBoost() {
    return !effects.isEmpty() && effects.stream().allMatch(e -> e.target() instanceof CardTarget);
  }

  /**
   * Returns the set of {@link ResourceTarget}s referenced by this card's effects. Used by {@link
   * PlayerState#areRequiredResourcesUnlocked(Card)} to determine whether this card can currently be
   * offered, drafted, or commissioned — a card naturally "requires" any resource it reads or writes
   * via its effects, regardless of whether that resource is locked by default.
   */
  public Set<ResourceTarget> requiredResources() {
    Set<ResourceTarget> required =
        effects.stream()
            .map(Effect::target)
            .filter(target -> target instanceof ResourceTarget)
            .map(target -> (ResourceTarget) target)
            .collect(Collectors.toCollection(HashSet::new));
    // A direct assault on the enemy's POPULATION (a damage card) requires a standing army, so it is
    // gated behind STRENGTH — unlocked by BARRACKS — even though the effect targets the opponent
    // rather than a self resource. This locks the attack cards behind the Barracks Great Work
    // without charging the player any STRENGTH to play them.
    boolean attacksPopulation =
        effects.stream()
            .anyMatch(
                e ->
                    e.target() instanceof OpponentResourceTarget opponent
                        && opponent.targetResource() == ResourceTarget.POPULATION);
    if (attacksPopulation) {
      required.add(ResourceTarget.STRENGTH);
    }
    return Set.copyOf(required);
  }

  /**
   * The card that replaces this one when construction completes. For regular cards (where {@link
   * #completedCard()} is {@code null}), returns {@code this} — normal cards resolve to themselves.
   */
  public Card resolvedCompletedCard() {
    return completedCard != null ? completedCard : this;
  }

  /**
   * Returns a new {@link Card} with the modifier of every effect whose target equals {@code
   * resource} increased by {@code delta}. All other effects are preserved unchanged. Used by the
   * engine-builder pass to permanently boost cards to the right of an engine-builder card during
   * {@link Phase#PLAY} resolution.
   *
   * @param resource the resource target to boost
   * @param delta the flat amount to add to each matching effect's modifier
   */
  public Card boostEffect(ResourceTarget resource, double delta) {
    List<Effect> boosted =
        effects.stream()
            .map(e -> (e.target() == resource) ? new Effect(e.target(), e.modifier() + delta) : e)
            .toList();
    return new Card(
        name,
        staffRequirement,
        productionRequirement,
        frequencyType,
        boosted,
        constructionPeriod,
        completedCard,
        unlockedResources,
        victory,
        season);
  }

  /**
   * Returns a new {@link Card} with the modifier of every matching effect increased by {@code
   * delta}, exactly like {@link #boostEffect}, except that if no effect targeting {@code resource}
   * exists yet, a brand-new effect of {@code delta} is appended instead of being silently dropped.
   *
   * <p>Used by Reform/Decree resolution on advisor cards: some advisors (e.g. Joric Wren, Brynn
   * Ostlar) start with an empty effects list because their base Specialty is meta-only, but a later
   * Decree can still grant them a plain resource effect (e.g. Ostlar's "Build the Stables" Decree
   * adds a Production boost where none existed before). {@link #boostEffect} alone can't express
   * that first-time addition.
   *
   * @param resource the resource target to boost or add
   * @param delta the flat amount to add to the matching effect, or the modifier of the new effect
   */
  public Card withEffectBoostOrAdd(ResourceTarget resource, double delta) {
    boolean hasMatch = effects.stream().anyMatch(e -> e.target() == resource);
    if (hasMatch) {
      return boostEffect(resource, delta);
    }
    List<Effect> withAdded = new ArrayList<>(effects);
    withAdded.add(new Effect(resource, delta));
    return new Card(
        name,
        staffRequirement,
        productionRequirement,
        frequencyType,
        withAdded,
        constructionPeriod,
        completedCard,
        unlockedResources,
        victory,
        season);
  }

  /**
   * Returns a new {@link Card} with identical requirements but every effect's modifier multiplied
   * by {@code factor}. Used internally when both cards have identical modifiers (no engine boost).
   *
   * @param factor the multiplier to apply to every effect modifier (e.g. {@code 2} to double)
   */
  public Card amplify(double factor) {
    List<Effect> amplified = effects.stream().map(e -> e.upgrade(factor)).toList();
    String upgradedName = name.endsWith(" II") ? name.replace(" II", " III") : name + " II";
    return new Card(
        upgradedName,
        staffRequirement,
        productionRequirement,
        frequencyType,
        amplified,
        constructionPeriod,
        completedCard,
        unlockedResources,
        victory,
        season);
  }

  /**
   * Returns a new {@link Card} whose effect modifiers are the sum of this card's modifiers and
   * {@code other}'s modifiers, paired by index. Effects present only in this card are kept
   * unchanged. Used by the upgrade path so that a boosted table card (e.g. Farm +4 from an engine)
   * correctly combines with an unmodified hand card: Farm(14) + Farm(10) → Farm II(24).
   *
   * @param other the hand card being played onto this table card
   */
  /**
   * Returns a new {@link Card} identical to this one but with {@link #constructionPeriod()} set to
   * {@code period}. Used by the engine during {@link Phase#PLAY} resolution to track per-season
   * construction progress in-place on the table card.
   */
  public Card withConstructionPeriod(int period) {
    return new Card(
        name,
        staffRequirement,
        productionRequirement,
        frequencyType,
        effects,
        Math.max(0, period),
        completedCard,
        unlockedResources,
        victory,
        season);
  }

  public Card mergeWith(Card other) {
    List<Effect> merged = new ArrayList<>(effects.size());
    for (int i = 0; i < effects.size(); i++) {
      Effect mine = effects.get(i);
      if (i < other.effects().size()) {
        Effect theirs = other.effects().get(i);
        merged.add(new Effect(mine.target(), mine.modifier() + theirs.modifier()));
      } else {
        merged.add(mine);
      }
    }
    String upgradedName = name.endsWith(" II") ? name.replace(" II", " III") : name + " II";
    return new Card(
        upgradedName,
        staffRequirement,
        productionRequirement,
        frequencyType,
        merged,
        constructionPeriod,
        completedCard,
        unlockedResources,
        victory,
        season);
  }

  /**
   * Plays this card by applying all of its {@link Effect}s to the provided {@link PlayerState}
   * scaled by the provided {@link StaffingTier}. A card is only played if its required {@link
   * StaffingTier} is met and its {@link Effect}s can be afforded. Affordance is only an issue for
   * great works, as they have negative effect modifiers (costs). The card is played with no {@link
   * SabotageContext} and default target cards (the cards following it on the season's board).
   *
   * @param state The {@link PlayerState} (kingdom) to apply effects to.
   * @param season The season whose table this card is resolving on.
   * @param tier The {@link StaffingTier} to use when applying effects.
   */
  public PlayedCard play(final PlayerState state, final Season season, final StaffingTier tier) {
    return play(
        state, season, tier, SabotageContext.none(), state.getCardsOnBoardAfter(season, this));
  }

  /**
   * Plays this card by applying all of its {@link Effect}s to the provided {@link PlayerState}
   * scaled by the provided {@link StaffingTier}. A card is only played if its required {@link
   * StaffingTier} is met and its {@link Effect}s can be afforded. Affordance is only an issue for
   * great works, as they have negative effect modifiers (costs). The card is played with tbe
   * provided {@link SabotageContext} and default target cards (the cards following it on the
   * season's board).
   *
   * @param state The {@link PlayerState} (kingdom) to apply effects to.
   * @param season The season whose table this card is resolving on.
   * @param tier The {@link StaffingTier} to use when applying effects.
   * @param sabotage The {@link SabotageContext} to apply.
   */
  public PlayedCard play(
      final PlayerState state,
      final Season season,
      final StaffingTier tier,
      final SabotageContext sabotage) {
    return play(state, season, tier, sabotage, state.getCardsOnBoardAfter(season, this));
  }

  /**
   * Plays this card exactly like {@link #play(PlayerState, Season, StaffingTier)}, additionally
   * resolving any {@link OpponentResourceTarget} (sabotage) effects against the frozen opponent
   * snapshot carried by {@code sabotage}. Sabotage deltas are only <em>collected</em> here; the
   * engine applies them to the live opponent after every table has resolved (two-phase apply).
   *
   * @param state A {@link PlayerState} to apply effects to.
   * @param season The season whose table this card is resolving on.
   * @param tier The {@link StaffingTier} to use when applying effects.
   * @param sabotage the cross-seat resolution context; use {@link SabotageContext#none()} when the
   *     match has no live opponent
   * @param targets The {@link Card}s to consider as targets for any {@link CardTarget} effect this
   *     card has
   */
  public PlayedCard play(
      final PlayerState state,
      final Season season,
      final StaffingTier tier,
      final SabotageContext sabotage,
      final List<Card> targets) {
    if (tier.greaterThanOrEqualTo(getRequiredStaffingTier()) && state.canAfford(effects())) {
      final ResourceState before = state.getResources();
      final List<Card> boostedCards = new ArrayList<>();

      for (Effect effect : getTierEffects(tier)) {
        if (effect.target() instanceof ResourceTarget rt) {
          state.apply(effect);
        } else if (effect.target() instanceof OpponentResourceTarget) {
          sabotage.collect(effect);
        } else if (effect.target() instanceof CardTarget ct) {
          for (final Card card : targets) {
            final Optional<Card> boosted = boost(card, effect, ct);
            boosted.ifPresent(
                boostedCard -> {
                  state.replaceTableCard(season, card, boostedCard);
                  boostedCards.add(boostedCard);
                });
            if (ct.adjacentOnly()) {
              break;
            }
          }
        }
      }

      if (isGreatWork()) {
        int newPeriod = constructionPeriod() - 1;
        if (newPeriod == 0) {
          // ── Construction COMPLETE — swap for the completed card ──────────
          state.replaceTableCard(season, this, completedCard());
          // Unlock any new resource types this Great Work grants.
          state.unlock(this);
        } else {
          // Decrement: replace the card on the table with the same card minus one period.
          Card decremented = withConstructionPeriod(newPeriod);
          state.replaceTableCard(season, this, decremented);
        }
      }

      return new PlayedCard(
          this,
          tier,
          true,
          state.getResources().getDelta(before),
          Collections.unmodifiableList(boostedCards));
    }

    return new PlayedCard(this, tier, false, ResourceState.empty(), List.of());
  }

  public Optional<Card> boost(final Card card, final Effect effect, final CardTarget target) {
    boolean hasMatch = card.effects().stream().anyMatch(e -> e.target() == target.targetResource());
    if (hasMatch) {
      return Optional.of(card.boostEffect(target.targetResource(), effect.modifier()));
    }
    return Optional.empty();
  }

  public List<Effect> getTierEffects(final StaffingTier tier) {
    switch (tier) {
      case NONE -> {
        return List.of();
      }
      case HALF -> {
        if (isGreatWork()) {
          // Great works only have effects when fully staffed
          return List.of();
        }
        return halveEffects(effects());
      }
      case FULL -> {
        return effects();
      }
    }
    return List.of();
  }

  /**
   * Returns a new effect list with every modifier floored to half, used when a card resolves at
   * {@link StaffingTier#HALF}.
   */
  private List<Effect> halveEffects(List<Effect> effects) {
    return effects.stream()
        .map(e -> new Effect(e.target(), Math.floor(e.modifier() / 2.0)))
        .toList();
  }

  /**
   * Returns the minimum required {@link StaffingTier} for this card to be played.
   *
   * @return the minimum required {@link StaffingTier} for this card to be played.
   */
  private StaffingTier getRequiredStaffingTier() {
    // Great works must be fully staffed to be played
    if (isGreatWork()) {
      return StaffingTier.FULL;
    }
    return StaffingTier.NONE;
  }
}
