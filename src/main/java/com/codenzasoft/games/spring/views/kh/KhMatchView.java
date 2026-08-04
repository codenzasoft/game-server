package com.codenzasoft.games.spring.views.kh;

import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.TimeState;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Wire view of the whole match as sent to one client seat: every kingdom (the viewer's own in full,
 * opponents redacted — see {@link KhKingdomView}) plus the shared clock, the readiness barrier's
 * {@code submittedSeats}, and the match outcome fields.
 *
 * @param players kingdom views ordered by seat; the client reads {@code players[context.seat]} as
 *     "you" and the other index as the opponent
 * @param time the shared match clock
 * @param matchOver {@code true} once the match is decided
 * @param winnerSeat seat of the winner, or {@code -1} if undecided (or a solo knock-out)
 * @param victoryType how the match was won: {@code "GREAT_WORKS"}, {@code "CONQUEST"}, or {@code
 *     "NONE"} — lets the game-over screen name the path to victory
 * @param submittedSeats seats that have completed the current phase; drives the client's "waiting
 *     for other player…" state
 */
public record KhMatchView(
    @JsonProperty("players") List<KhKingdomView> players,
    @JsonProperty("time") TimeState time,
    @JsonProperty("matchOver") boolean matchOver,
    @JsonProperty("winnerSeat") int winnerSeat,
    @JsonProperty("victoryType") String victoryType,
    @JsonProperty("submittedSeats") List<Integer> submittedSeats,
    @JsonProperty("resolutionPending") boolean resolutionPending) {

  /** Builds the match view for the client seated at {@code viewerSeat}. */
  public static KhMatchView forSeat(final GameState state, final int viewerSeat) {
    final List<KhKingdomView> players = new ArrayList<>(state.playerCount());
    for (int seat = 0; seat < state.playerCount(); seat++) {
      players.add(
          seat == viewerSeat
              ? KhKingdomView.full(state.getPlayer(seat))
              : KhKingdomView.redacted(state.getPlayer(seat)));
    }
    return new KhMatchView(
        players,
        state.getTime(),
        state.isMatchOver(),
        state.winnerSeat(),
        state.victoryType(),
        state.getSubmittedSeats(),
        state.isResolutionPending());
  }
}
