package com.codenzasoft.games.spring.services.hearts;

import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.RoundState;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A snapshot of one bot strategy decision: the round state the bot saw before playing, the set of
 * legal cards available, and the card it chose.
 */
public record BotDecision(
    Instant timestamp,
    UUID gameId,
    UUID playerUUID,
    String playerName,
    int roundNumber,
    int trickNumber,
    RoundState roundState,
    Set<Card> legalCards,
    Card cardPlayed) {}
