package com.codenzasoft.games.kingshand;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable runtime state for an {@link Advisor} who has been hired onto the council. References
 * the static {@link Advisor} catalog entry by {@link #getAdvisorId()}; the remaining components
 * capture how Reforms and Decrees have permanently changed this advisor over the life of a run:
 * {@link #getCard()} accumulates boosted/added effects the way a table card does, and the three
 * bonus counters track stacking improvements to the meta-mechanic Specialties (Joric's free
 * rerolls, Varn's extra ADD candidates, Ostlar's cross-table boost amount) without ever mutating
 * the shared {@link Advisor} catalog constant.
 *
 * <p>All mutation is expressed as {@code with*} methods returning a new instance.
 */
public record SeatedAdvisor(
    String advisorId,
    Card card,
    int reformStacksTaken,
    boolean decreeTaken,
    int lastProposalYear,
    int bonusFreeRerolls,
    int bonusExtraAddCandidates,
    double bonusCrossTableBoost,
    int hiredYear) {

  /**
   * Seats a freshly hired advisor with default counters. {@code lastProposalYear} starts at -1 so
   * it sorts before any real (1-indexed) year, making a new hire the rotation winner for the next
   * COUNCIL_MEETING.
   */
  public static SeatedAdvisor of(String advisorId, Card card, int hiredYear) {
    return new SeatedAdvisor(advisorId, card, 0, false, -1, 0, 0, 0.0, hiredYear);
  }

  // ----- Read accessors (kept as getX() to preserve existing call sites) -----

  @JsonProperty("advisorId")
  public String getAdvisorId() {
    return advisorId;
  }

  @JsonProperty("card")
  public Card getCard() {
    return card;
  }

  @JsonProperty("reformStacksTaken")
  public int getReformStacksTaken() {
    return reformStacksTaken;
  }

  @JsonProperty("decreeTaken")
  public boolean isDecreeTaken() {
    return decreeTaken;
  }

  @JsonProperty("lastProposalYear")
  public int getLastProposalYear() {
    return lastProposalYear;
  }

  @JsonProperty("bonusFreeRerolls")
  public int getBonusFreeRerolls() {
    return bonusFreeRerolls;
  }

  @JsonProperty("bonusExtraAddCandidates")
  public int getBonusExtraAddCandidates() {
    return bonusExtraAddCandidates;
  }

  @JsonProperty("bonusCrossTableBoost")
  public double getBonusCrossTableBoost() {
    return bonusCrossTableBoost;
  }

  @JsonProperty("hiredYear")
  public int getHiredYear() {
    return hiredYear;
  }

  // ----- with* mutators -----

  public SeatedAdvisor withCard(Card card) {
    return new SeatedAdvisor(
        advisorId,
        card,
        reformStacksTaken,
        decreeTaken,
        lastProposalYear,
        bonusFreeRerolls,
        bonusExtraAddCandidates,
        bonusCrossTableBoost,
        hiredYear);
  }

  public SeatedAdvisor withReformStacksTaken(int reformStacksTaken) {
    return new SeatedAdvisor(
        advisorId,
        card,
        reformStacksTaken,
        decreeTaken,
        lastProposalYear,
        bonusFreeRerolls,
        bonusExtraAddCandidates,
        bonusCrossTableBoost,
        hiredYear);
  }

  /** This advisor with its reform-stack count incremented by one. */
  public SeatedAdvisor incrementReformStacksTaken() {
    return withReformStacksTaken(reformStacksTaken + 1);
  }

  public SeatedAdvisor withDecreeTaken(boolean decreeTaken) {
    return new SeatedAdvisor(
        advisorId,
        card,
        reformStacksTaken,
        decreeTaken,
        lastProposalYear,
        bonusFreeRerolls,
        bonusExtraAddCandidates,
        bonusCrossTableBoost,
        hiredYear);
  }

  public SeatedAdvisor withLastProposalYear(int lastProposalYear) {
    return new SeatedAdvisor(
        advisorId,
        card,
        reformStacksTaken,
        decreeTaken,
        lastProposalYear,
        bonusFreeRerolls,
        bonusExtraAddCandidates,
        bonusCrossTableBoost,
        hiredYear);
  }

  /** This advisor with {@code delta} added to its bonus free-reroll count. */
  public SeatedAdvisor addBonusFreeRerolls(int delta) {
    return new SeatedAdvisor(
        advisorId,
        card,
        reformStacksTaken,
        decreeTaken,
        lastProposalYear,
        bonusFreeRerolls + delta,
        bonusExtraAddCandidates,
        bonusCrossTableBoost,
        hiredYear);
  }

  /** This advisor with {@code delta} added to its bonus extra-ADD-candidate count. */
  public SeatedAdvisor addBonusExtraAddCandidates(int delta) {
    return new SeatedAdvisor(
        advisorId,
        card,
        reformStacksTaken,
        decreeTaken,
        lastProposalYear,
        bonusFreeRerolls,
        bonusExtraAddCandidates + delta,
        bonusCrossTableBoost,
        hiredYear);
  }

  /** This advisor with {@code delta} added to its cross-table boost amount. */
  public SeatedAdvisor addBonusCrossTableBoost(double delta) {
    return new SeatedAdvisor(
        advisorId,
        card,
        reformStacksTaken,
        decreeTaken,
        lastProposalYear,
        bonusFreeRerolls,
        bonusExtraAddCandidates,
        bonusCrossTableBoost + delta,
        hiredYear);
  }
}
