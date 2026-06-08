package com.codenzasoft.games.spring.messages;

import com.codenzasoft.games.hearts.HeartsState;

/**
 * A STOMP message for a specific player and {@link com.codenzasoft.games.hearts.HeartsGame}.
 *
 * @param context A {@link HeartsContext} providing message context.
 * @param state The associated {@link HeartsState}.
 */
public record HeartsMessage(HeartsContext context, HeartsState state) {}
