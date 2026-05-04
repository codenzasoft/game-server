package com.codenzasoft.games.kingshand;

public record Card(
    double populationRequirement,
    double productionRequirement,
    Effect populationEffect,
    Effect productionEffect) {

  public double nextPopulation(double population) {
    return populationEffect.nextValue(population);
  }

  public double nextProduction(double production) {
    return productionEffect.nextValue(production);
  }
}
