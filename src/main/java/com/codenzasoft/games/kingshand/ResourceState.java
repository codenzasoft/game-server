package com.codenzasoft.games.kingshand;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record ResourceState(
    double food,
    double production,
    double population,
    double populationCap,
    double gold,
    double strength,
    Set<ResourceTarget> unlocked) {

  public static ResourceState empty() {
    return new ResourceState(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Collections.emptySet());
  }

  public ResourceState apply(Effect effect) {

    if (effect.target() instanceof ResourceTarget rt) {
      switch (rt) {
        case POPULATION:
          double clamped = Math.max(0.0, Math.min(nextValue(effect, population), populationCap));
          // A fractional population (< 1) means nobody is actually alive.
          if (clamped < 1.0) {
            return new ResourceState(
                food, production, 0.0, populationCap, gold, strength, unlocked);
          } else {
            return new ResourceState(
                food, production, clamped, populationCap, gold, strength, unlocked);
          }

        case PRODUCTION:
          return new ResourceState(
              food,
              nextValue(effect, production),
              population,
              populationCap,
              gold,
              strength,
              unlocked);
        case FOOD:
          return new ResourceState(
              nextValue(effect, food),
              production,
              population,
              populationCap,
              gold,
              strength,
              unlocked);
        case POPULATION_CAP:
          return new ResourceState(
              food,
              production,
              population,
              nextValue(effect, populationCap),
              gold,
              strength,
              unlocked);
        case GOLD:
          if (unlocked.contains(ResourceTarget.GOLD)) {
            return new ResourceState(
                food,
                production,
                population,
                populationCap,
                nextValue(effect, gold),
                strength,
                unlocked);
          }
          break;

        case STRENGTH:
          if (unlocked.contains(ResourceTarget.STRENGTH)) {
            return new ResourceState(
                food,
                production,
                population,
                populationCap,
                gold,
                nextValue(effect, strength),
                unlocked);
          }
          break;
      }
    }
    return this;
  }

  private double nextValue(Effect effect, double value) {
    return Math.max(0.0, (effect.apply(value)));
  }

  public ResourceState addProduction(double value) {
    double newProduction = Math.max(0.0, (production + value));
    return new ResourceState(
        food, newProduction, population, populationCap, gold, strength, unlocked);
  }

  public ResourceState addGold(double value) {
    if (unlocked.contains(ResourceTarget.GOLD)) {
      double newGold = Math.max(0.0, (gold + value));
      return new ResourceState(
          food, production, population, populationCap, newGold, strength, unlocked);
    }
    return this;
  }

  public ResourceState addPopulationCap(double value) {
    double newPopulationCap = Math.max(0.0, (populationCap + value));
    return new ResourceState(
        food, production, population, newPopulationCap, gold, strength, unlocked);
  }

  /** Permanently unlocks every resource {@code card} unlocks (e.g. on Great Work completion). */
  public ResourceState unlock(Card card) {

    Set<ResourceTarget> nextUnlocked = new HashSet<>(unlocked);
    nextUnlocked.addAll(card.unlockedResources());
    return new ResourceState(
        food,
        production,
        population,
        populationCap,
        gold,
        strength,
        Collections.unmodifiableSet(nextUnlocked));
  }

  public ResourceState consumeFood(double rate) {
    double consumed = Math.floor(population * rate);
    double nextFood = food - consumed;
    if (food < consumed) {
      double deficit = consumed - food;
      nextFood = 0.0;
      double nextPopulation = population - (0.5 * deficit);
      return new ResourceState(
          nextFood, production, nextPopulation, populationCap, gold, strength, unlocked);
    }

    // Surplus growth: if food remains after consumption, grow population proportionally.
    // Growth rate is capped at 20% per season so the population snowball stays manageable.
    // The surplus food is not consumed — it carries over to the next season.
    double growthRate = Math.min(nextFood / population, 0.20);
    double nextPopulation = Math.min((population + (population * growthRate)), populationCap);
    return new ResourceState(
        nextFood, production, nextPopulation, populationCap, gold, strength, unlocked);
  }

  public ResourceState getDelta(final ResourceState since) {
    final Set<ResourceTarget> unlockedSince = new HashSet<>(unlocked);
    unlockedSince.removeAll(since.unlocked);
    return new ResourceState(
        food - since.food,
        production - since.production,
        population - since.population,
        populationCap - since.populationCap,
        gold - since.gold,
        strength - since.strength,
        Collections.unmodifiableSet(unlockedSince));
  }
}
