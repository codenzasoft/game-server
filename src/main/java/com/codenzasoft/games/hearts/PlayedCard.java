package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.cards.Card;

/**
 * Identifies a {@link Card} played in a trick.
 *
 * @param player Player name
 * @param card The {@link Card} played
 */
public record PlayedCard(String player, Card card) {}
