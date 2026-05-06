package com.codenzasoft.games.spring.messages;

import com.codenzasoft.games.xo.XoState;

/**
 * A STOMP message for a specific player and {@link com.codenzasoft.games.xo.XoGame}.
 *
 * @param context A {@link XoContext} providing message context.
 * @param state The associated {@link XoState}.
 */
public record XoMessage(XoContext context, XoState state) {}
