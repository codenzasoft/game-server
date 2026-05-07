package com.codenzasoft.games.kingshand;

/**
 * Targets another card on the same season's table.
 *
 * @param slotOffset relative slot offset from the source card's slot — e.g. {@code -1} for the card
 *     immediately to the left, {@code +1} for the card to the right.
 */
public record CardTarget(int slotOffset) implements EffectTarget {}
