package com.codenzasoft.games.spring.messages;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * Provides context for a specific {@link Player} in a {@link HeartsMessage}.
 *
 * @param player The name of the {@link Player} that is the recipient of the message.
 * @param gameId The id of the game being played.
 * @param hand The player's current hand of {@link Card}s.
 * @param validCards A collection of valid {@link Card}s to play, or empty if none.
 * @param nextGameId The id of the next game to play, possibly <code>null</code>.
 */
public record HeartsContext(
    @JsonProperty("player") String player,
    @JsonProperty("gameId") UUID gameId,
    @JsonProperty("hand") List<Card> hand,
    @JsonProperty("validCads") List<Card> validCards,
    @JsonInclude(JsonInclude.Include.NON_NULL) UUID nextGameId) {}
