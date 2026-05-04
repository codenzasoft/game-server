package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.List;

public class GameState {

  public List<SeasonTable> board;
  public double population;
  public double production;
  public Season curSeason;
  public Phase curPhase;
  public double year;

  public GameState() {
    // start with an empty board (4 empty season tables, 20 empty card slots)
    board = new ArrayList<>();

    board.add(new SeasonTable(Season.SPRING));
    board.add(new SeasonTable(Season.SUMMER));
    board.add(new SeasonTable(Season.FALL));
    board.add(new SeasonTable(Season.WINTER));

    // initialize with some production and population to get started
    this.population = 5;
    this.production = 100;

    // start in spring, year 1
    this.curSeason = Season.SPRING;
    this.curPhase = Phase.ADD;
    this.year = 1;
  }

  public void advancePhase() {
    // if play phase ends, move to the next season
    if (curPhase == Phase.PLAY) {
      advanceSeason();
    }

    curPhase = curPhase.next();
  }

  public void advanceSeason() {
    // new year if we end winter
    if (curSeason == Season.WINTER) {
      year++;
    }

    curSeason = curSeason.next();

    triggerCurrentSeasonTable();
  }

  public SeasonTable getCurrentTable() {
    return board.get(curSeason.ordinal());
  }

  private void triggerCurrentSeasonTable() {
    SeasonTable table = getCurrentTable();

    // trigger all cards on the table and update populationa and production accordingly
    for (Card card : table.cards) {
      if (card.populationEffect().frequencyType() == FrequencyType.RECURRING) {
        population = card.nextPopulation(population);
      }
      if (card.productionEffect().frequencyType() == FrequencyType.RECURRING) {
        production = card.nextProduction(production);
      }
    }
  }
}
