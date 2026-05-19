package com.codenzasoft.games.spring.messages;

import java.util.UUID;

/**
 * Provides context for a {@link KhMessage}.
 *
 * @param player The name of the player that is the recipient of the message.
 * @param gameId The id of the game being played.
 */
public record KhContext(String player, UUID gameId) {}
