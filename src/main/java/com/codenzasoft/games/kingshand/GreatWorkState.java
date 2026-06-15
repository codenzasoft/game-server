package com.codenzasoft.games.kingshand;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-game mutable state for a single {@link GreatWork}.
 *
 * <p>Lifecycle:
 *
 * <ul>
 *   <li>Starts uncreated ({@code commissioned = false}).
 *   <li>When the player commissions it, {@code commissioned} becomes {@code true} and
 *       {@code remainingPeriod} is set to the definition's {@link GreatWork#initialPeriod()}.
 *   <li>Each season the construction check passes, {@code remainingPeriod} decrements by one.
 *   <li>When {@code remainingPeriod} reaches zero, {@code completed} is set to {@code true} and
 *       the under-construction card is swapped for the completed card on the table.
 * </ul>
 *
 * <p>This class is intentionally not tied to the {@link GreatWork} definition at serialisation
 * time — only the name is persisted; the definition is looked up via {@link GreatWorkCatalog}.
 */
public class GreatWorkState {

  private final String greatWorkName;
  private int remainingPeriod;
  private boolean commissioned;
  private boolean completed;

  /**
   * Constructs a fresh (uncommissioned) state for the named Great Work.
   *
   * @param greatWorkName the stable name of the {@link GreatWork} definition
   * @param initialPeriod the construction period copied from the definition
   */
  public GreatWorkState(String greatWorkName, int initialPeriod) {
    this.greatWorkName = greatWorkName;
    this.remainingPeriod = initialPeriod;
    this.commissioned = false;
    this.completed = false;
  }

  /** Jackson deserialization constructor. */
  @JsonCreator
  public GreatWorkState(
      @JsonProperty("greatWorkName") String greatWorkName,
      @JsonProperty("remainingPeriod") int remainingPeriod,
      @JsonProperty("commissioned") boolean commissioned,
      @JsonProperty("completed") boolean completed) {
    this.greatWorkName = greatWorkName;
    this.remainingPeriod = remainingPeriod;
    this.commissioned = commissioned;
    this.completed = completed;
  }

  @JsonProperty("greatWorkName")
  public String getGreatWorkName() {
    return greatWorkName;
  }

  @JsonProperty("remainingPeriod")
  public int getRemainingPeriod() {
    return remainingPeriod;
  }

  @JsonProperty("commissioned")
  public boolean isCommissioned() {
    return commissioned;
  }

  @JsonProperty("completed")
  public boolean isCompleted() {
    return completed;
  }

  public void setCommissioned(boolean commissioned) {
    this.commissioned = commissioned;
  }

  public void setCompleted(boolean completed) {
    this.completed = completed;
  }

  /** Decrements the remaining period by one, flooring at zero. */
  public void decrementPeriod() {
    remainingPeriod = Math.max(0, remainingPeriod - 1);
  }
}
