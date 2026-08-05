package com.codenzasoft.games.kingshand;

/**
 * The outcome of playing a {@link Card} against an (immutable) {@link PlayerState}: the resulting
 * kingdom after the card's effects were applied, paired with the {@link PlayedCard} resolution
 * record used for logging and animation. The caller threads {@link #state()} forward and appends
 * {@link #played()}'s resolution step to its log.
 *
 * @param state the kingdom after this card resolved
 * @param played the resolution result (fired flag, resource delta, boosted cards)
 */
public record PlayResult(PlayerState state, PlayedCard played) {}
