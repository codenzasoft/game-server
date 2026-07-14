package com.codenzasoft.games.spring.views.kh;

import com.codenzasoft.games.kingshand.Advisor;
import com.codenzasoft.games.kingshand.Card;
import com.codenzasoft.games.kingshand.CardStaffing;
import com.codenzasoft.games.kingshand.CouncilProposal;
import com.codenzasoft.games.kingshand.PlayerState;
import com.codenzasoft.games.kingshand.ResolutionStep;
import com.codenzasoft.games.kingshand.ResourceState;
import com.codenzasoft.games.kingshand.Season;
import com.codenzasoft.games.kingshand.SeatedAdvisor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Wire view of one kingdom ({@link PlayerState}) as sent to a specific client seat.
 *
 * <p><b>Hidden-information model:</b> a client sees its own kingdom in full; opponent kingdoms are
 * <em>redacted</em> — the hand, ADD-phase offer, council offer, current proposal, and both card
 * piles are stripped. Tables (the board), resources, the seated council, and the resolution log
 * stay visible: they are public information the resolution screen and opponent HUD need.
 *
 * <p>Also computes {@code tableStaffingBySeason} — the old flat wire's {@code tableStaffing} was a
 * computed view property, re-exposed here per seat and per season (the client picks the current
 * season's entry).
 */
public class KhKingdomView {

  private final PlayerState player;
  private final boolean redacted;

  private KhKingdomView(final PlayerState player, final boolean redacted) {
    this.player = player;
    this.redacted = redacted;
  }

  /** Full-fidelity view of the viewer's own kingdom. */
  public static KhKingdomView full(final PlayerState player) {
    return new KhKingdomView(player, false);
  }

  /** Redacted view of an opponent kingdom (hand, offers, proposal, and piles stripped). */
  public static KhKingdomView redacted(final PlayerState player) {
    return new KhKingdomView(player, true);
  }

  @JsonProperty("board")
  public Map<Season, List<Card>> getBoard() {
    return player.getBoard();
  }

  @JsonProperty("resources")
  public ResourceState getResources() {
    return player.getResources();
  }

  @JsonProperty("hand")
  public List<Card> getHand() {
    return redacted ? List.of() : player.getHand();
  }

  @JsonProperty("drawPile")
  public Map<Season, List<Card>> getDrawPile() {
    return redacted ? emptyPiles() : player.getDrawPile();
  }

  @JsonProperty("discardPile")
  public Map<Season, List<Card>> getDiscardPile() {
    return redacted ? emptyPiles() : player.getDiscardPile();
  }

  @JsonProperty("addChoices")
  public List<Card> getAddChoices() {
    return redacted ? List.of() : player.getAddChoices();
  }

  @JsonProperty("rerollCount")
  public int getRerollCount() {
    return redacted ? 0 : player.getRerollCount();
  }

  @JsonProperty("resolutionLog")
  public List<ResolutionStep> getResolutionLog() {
    return player.getResolutionLog();
  }

  @JsonProperty("gameOver")
  public boolean isGameOver() {
    return player.isGameOver();
  }

  @JsonProperty("gameWon")
  public boolean isGameWon() {
    return player.isGameWon();
  }

  @JsonProperty("forfeited")
  public boolean isForfeited() {
    return player.isForfeited();
  }

  @JsonProperty("council")
  public List<SeatedAdvisor> getCouncil() {
    return player.getCouncil();
  }

  @JsonProperty("councilOfferChoices")
  public List<Advisor> getCouncilOfferChoices() {
    return redacted ? List.of() : player.getCouncilOfferChoices();
  }

  @JsonProperty("currentProposal")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public CouncilProposal getCurrentProposal() {
    return redacted ? null : player.getCurrentProposal();
  }

  @JsonProperty("permanentFreeRerolls")
  public int getPermanentFreeRerolls() {
    return player.getPermanentFreeRerolls();
  }

  @JsonProperty("acceleratedGreatWorkNames")
  public java.util.Set<String> getAcceleratedGreatWorkNames() {
    return player.getAcceleratedGreatWorkNames();
  }

  /**
   * Worker staffing for every season's table, computed from the board and current population.
   * Public information (drives staffing pips on both your own and the opponent's tables).
   */
  @JsonProperty("tableStaffingBySeason")
  public Map<Season, List<CardStaffing>> getTableStaffingBySeason() {
    final Map<Season, List<CardStaffing>> staffing = new EnumMap<>(Season.class);
    for (final Season season : Season.values()) {
      staffing.put(
          season, CardStaffing.computeStaffing(player.getTable(season), player.getResources()));
    }
    return staffing;
  }

  private static Map<Season, List<Card>> emptyPiles() {
    final Map<Season, List<Card>> empty = new EnumMap<>(Season.class);
    for (final Season season : Season.values()) {
      empty.put(season, List.of());
    }
    return empty;
  }
}
