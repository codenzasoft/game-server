package com.codenzasoft.games.kingshand;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record ResourceState(
    @JsonProperty("food") double food,
    @JsonProperty("production") double production,
    @JsonProperty("population") double population,
    @JsonProperty("populationCap") double populationCap,
    @JsonProperty("gold") double gold,
    @JsonProperty("strength") double strength,
    @JsonProperty("unlockedResources") Set<ResourceTarget> unlocked) {

  public static ResourceState empty() {
    return new ResourceState(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Collections.emptySet());
  }

  public ResourceState withFood(double food) {
    return new ResourceState(
        food,
        this.production,
        this.population,
        this.populationCap,
        this.gold,
        this.strength,
        this.unlocked);
  }

  public ResourceState withProduction(double production) {
    return new ResourceState(
        this.food,
        production,
        this.population,
        this.populationCap,
        this.gold,
        this.strength,
        this.unlocked);
  }

  public ResourceState withPopulation(double population) {
    return new ResourceState(
        this.food,
        this.production,
        population,
        this.populationCap,
        this.gold,
        this.strength,
        this.unlocked);
  }

  public ResourceState withPopulationCap(double populationCap) {
    return new ResourceState(
        this.food,
        this.production,
        this.population,
        populationCap,
        this.gold,
        this.strength,
        this.unlocked);
  }

  public ResourceState withGold(double gold) {
    return new ResourceState(
        this.food,
        this.production,
        this.population,
        this.populationCap,
        gold,
        this.strength,
        this.unlocked);
  }

  public ResourceState withStrength(double strength) {
    return new ResourceState(
        this.food,
        this.production,
        this.population,
        this.populationCap,
        this.gold,
        strength,
        this.unlocked);
  }

  public ResourceState withUnlocked(Set<ResourceTarget> unlocked) {
    return new ResourceState(
        this.food,
        this.production,
        this.population,
        this.populationCap,
        this.gold,
        this.strength,
        Collections.unmodifiableSet(unlocked));
  }

  public ResourceState apply(Effect effect) {

    if (effect.target() instanceof ResourceTarget rt) {
      switch (rt) {
        case POPULATION:
          double clamped = Math.max(0.0, Math.min(nextValue(effect, population), populationCap));
          // A fractional population (< 1) means nobody is actually alive.
          if (clamped < 1.0) {
            return withPopulation(0.0);
          } else {
            return withPopulation(clamped);
          }

        case PRODUCTION:
          return withProduction(nextValue(effect, production));
        case FOOD:
          return withFood(nextValue(effect, food));
        case POPULATION_CAP:
          return withPopulationCap(nextValue(effect, populationCap));
        case GOLD:
          if (unlocked.contains(ResourceTarget.GOLD)) {
            return withGold(nextValue(effect, gold));
          }
          break;
        case STRENGTH:
          if (unlocked.contains(ResourceTarget.STRENGTH)) {
            return withStrength(nextValue(effect, strength));
          }
          break;
      }
    }
    return this;
  }

  private double nextValue(Effect effect, double value) {
    return clampZero(effect.apply(value));
  }

  private double clampZero(double value) {
    return Math.max(0.0, value);
  }

  public ResourceState addProduction(double value) {
    return withProduction(clampZero(production + value));
  }

  public ResourceState addGold(double value) {
    if (unlocked.contains(ResourceTarget.GOLD)) {
      return withGold(clampZero(gold + value));
    }
    return this;
  }

  public ResourceState addPopulationCap(double value) {
    return withPopulationCap(clampZero(populationCap + value));
  }

  /** Permanently unlocks every resource {@code card} unlocks (e.g. on Great Work completion). */
  public ResourceState unlock(Card card) {

    Set<ResourceTarget> nextUnlocked = new HashSet<>(unlocked);
    nextUnlocked.addAll(card.unlockedResources());
    return withUnlocked(nextUnlocked);
  }

  public ResourceState consumeFood(double rate) {
    double consumed = Math.floor(population * rate);
    if (consumed > food) {
      double deficit = consumed - food;
      double nextPopulation = clampZero(population - (0.5 * deficit));
      return withFood(0.0).withPopulation(nextPopulation);
    } else {
      double nextFood = food - consumed;
      // Surplus growth: if food remains after consumption, grow population proportionally.
      // Growth rate is capped at 20% per season so the population snowball stays manageable.
      // The surplus food is not consumed — it carries over to the next season.
      double growthRate = Math.min(nextFood / population, 0.20);
      double nextPopulation = Math.min((population + (population * growthRate)), populationCap);
      return withFood(nextFood).withPopulation(nextPopulation);
    }
  }

  /**
   * Returns a {@link ResourceState} containing delta values between this and the provided previous
   * {@link ResourceState}.
   *
   * @param previous A {@link ResourceState}
   * @return A {@link ResourceState} with the difference in values between this state and the
   *     previous state.
   */
  public ResourceState getDelta(final ResourceState previous) {
    final Set<ResourceTarget> unlockedSince = new HashSet<>(unlocked);
    unlockedSince.removeAll(previous.unlocked);
    return new ResourceState(
        food - previous.food,
        production - previous.production,
        population - previous.population,
        populationCap - previous.populationCap,
        gold - previous.gold,
        strength - previous.strength,
        Collections.unmodifiableSet(unlockedSince));
  }

  public boolean isUnlocked(final ResourceTarget target) {
    return unlocked.contains(target);
  }
}
