package com.codenzasoft.games.spring.messages;

import com.codenzasoft.games.kingshand.GameState;

/**
 * A STOMP message for a specific player and King's Hand game.
 *
 * @param context A {@link KhContext} providing message context.
 * @param state The associated {@link GameState}.
 */
public record KhMessage(KhContext context, GameState state) {}
