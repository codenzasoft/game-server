package com.codenzasoft.games.kingshand;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Runtime state for an {@link Advisor} who has actually been hired onto the council. Mutable
 * (unlike the static {@link Advisor} catalog entry it references by {@link #getAdvisorId()})
 * because Reforms and Decrees permanently change it over the life of a run: {@link #getCard()}
 * accumulates boosted/added effects exactly the way a table card does via {@link
 * Card#withEffectBoostOrAdd}, and the three bonus counters track stacking improvements to the three
 * meta-mechanic Specialties (Joric's free rerolls, Varn's extra ADD candidates, Ostlar's
 * cross-table boost amount) without ever mutating the shared {@link Advisor} catalog constant.
 */
public class SeatedAdvisor {

  private final String advisorId;
  private Card card;
  private int reformStacksTaken;
  private boolean decreeTaken;
  private int lastProposalYear;
  private int bonusFreeRerolls;
  private int bonusExtraAddCandidates;
  private double bonusCrossTableBoost;
  private final int hiredYear;

  public SeatedAdvisor(String advisorId, Card card, int hiredYear) {
    this.advisorId = advisorId;
    this.card = card;
    this.reformStacksTaken = 0;
    this.decreeTaken = false;
    // -1 sorts before any real year (years are 1-indexed), so a freshly hired advisor is always
    // the rotation winner for the very next COUNCIL_MEETING.
    this.lastProposalYear = -1;
    this.bonusFreeRerolls = 0;
    this.bonusExtraAddCandidates = 0;
    this.bonusCrossTableBoost = 0;
    this.hiredYear = hiredYear;
  }

  /** Constructor used when restoring full state (e.g. from tests); no defaults applied. */
  public SeatedAdvisor(
      String advisorId,
      Card card,
      int reformStacksTaken,
      boolean decreeTaken,
      int lastProposalYear,
      int bonusFreeRerolls,
      int bonusExtraAddCandidates,
      double bonusCrossTableBoost,
      int hiredYear) {
    this.advisorId = advisorId;
    this.card = card;
    this.reformStacksTaken = reformStacksTaken;
    this.decreeTaken = decreeTaken;
    this.lastProposalYear = lastProposalYear;
    this.bonusFreeRerolls = bonusFreeRerolls;
    this.bonusExtraAddCandidates = bonusExtraAddCandidates;
    this.bonusCrossTableBoost = bonusCrossTableBoost;
    this.hiredYear = hiredYear;
  }

  @JsonProperty("advisorId")
  public String getAdvisorId() {
    return advisorId;
  }

  @JsonProperty("card")
  public Card getCard() {
    return card;
  }

  public void setCard(Card card) {
    this.card = card;
  }

  @JsonProperty("reformStacksTaken")
  public int getReformStacksTaken() {
    return reformStacksTaken;
  }

  public void incrementReformStacksTaken() {
    this.reformStacksTaken++;
  }

  @JsonProperty("decreeTaken")
  public boolean isDecreeTaken() {
    return decreeTaken;
  }

  public void setDecreeTaken(boolean decreeTaken) {
    this.decreeTaken = decreeTaken;
  }

  @JsonProperty("lastProposalYear")
  public int getLastProposalYear() {
    return lastProposalYear;
  }

  public void setLastProposalYear(int lastProposalYear) {
    this.lastProposalYear = lastProposalYear;
  }

  @JsonProperty("bonusFreeRerolls")
  public int getBonusFreeRerolls() {
    return bonusFreeRerolls;
  }

  public void addBonusFreeRerolls(int delta) {
    this.bonusFreeRerolls += delta;
  }

  @JsonProperty("bonusExtraAddCandidates")
  public int getBonusExtraAddCandidates() {
    return bonusExtraAddCandidates;
  }

  public void addBonusExtraAddCandidates(int delta) {
    this.bonusExtraAddCandidates += delta;
  }

  @JsonProperty("bonusCrossTableBoost")
  public double getBonusCrossTableBoost() {
    return bonusCrossTableBoost;
  }

  public void addBonusCrossTableBoost(double delta) {
    this.bonusCrossTableBoost += delta;
  }

  @JsonProperty("hiredYear")
  public int getHiredYear() {
    return hiredYear;
  }
}
