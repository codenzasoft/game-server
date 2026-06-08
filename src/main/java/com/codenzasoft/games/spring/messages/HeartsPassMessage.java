package com.codenzasoft.games.spring.messages;

import com.codenzasoft.games.common.cards.Card;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Payload for a Hearts pass-card selection submitted by a player. */
public record HeartsPassMessage(@JsonProperty("cards") List<Card> cards) {}
