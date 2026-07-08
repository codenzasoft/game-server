package com.codenzasoft.games.spring.messages;

import java.util.UUID;

/**
 * Provides context for a {@link KhMessage}.
 *
 * @param player The name of the player that is the recipient of the message.
 * @param gameId The id of the game being played.
 * @param seat The recipient's seat index in the match, so the client can identify which kingdom in
 *     {@code players[]} is "you" versus the opponent(s).
 */
public record KhContext(String player, UUID gameId, int seat) {}
