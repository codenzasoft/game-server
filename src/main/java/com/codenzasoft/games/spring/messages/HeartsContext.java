package com.codenzasoft.games.spring.messages;

import com.codenzasoft.games.common.Player;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Provides context for a {@link HeartsMessage}.
 *
 * @param player The name of the {@link Player} that is the recipient of the message.
 * @param gameId The id of the game being played.
 * @param nextGameId The id of the next game to play, possibly <code>null</code>.
 */
public record HeartsContext(
    String player, UUID gameId, @JsonInclude(JsonInclude.Include.NON_NULL) UUID nextGameId) {}
