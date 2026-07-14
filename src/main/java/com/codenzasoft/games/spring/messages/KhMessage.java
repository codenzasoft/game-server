package com.codenzasoft.games.spring.messages;

import com.codenzasoft.games.spring.views.kh.KhMatchView;

/**
 * A STOMP message for a specific player and King's Hand game.
 *
 * <p>The state payload is a per-seat {@link KhMatchView} rather than the raw engine state: the
 * recipient's own kingdom is sent in full while opponent kingdoms are redacted (hand, offers, and
 * piles stripped — the hidden-information model).
 *
 * @param context A {@link KhContext} providing message context.
 * @param state The associated per-seat {@link KhMatchView}.
 */
public record KhMessage(KhContext context, KhMatchView state) {}
