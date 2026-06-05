package com.codenzasoft.games.kingshand;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single step in the end-of-season resolution sequence, sent to the client inside {@link
 * GameState#getResolutionLog()} so the frontend can drive indicators and toasts directly from
 * server-computed data rather than re-simulating game logic.
 *
 * <p>Resource deltas reflect the actual change (post-clamping, post-cap) that occurred during this
 * step, so the client never needs to replicate Java rounding or clamping.
 *
 * <p>Special cases:
 *
 * <ul>
 *   <li>{@code cardName == null}: the food-consumption / population-growth step at the end.
 *   <li>{@code engineBoost == true}: the card fired engine-builder (CardTarget) effects; resource
 *       deltas are zero (no direct resource change), but the target cards' modifiers have been
 *       permanently updated in the state.
 *   <li>{@code staffingTier == NONE}: card did not fire (understaffed); all deltas are zero.
 * </ul>
 */
public record ResolutionStep(
    /** Name of the card that fired, or {@code null} for the food-consumption step. */
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("cardName") String cardName,

    /** How the card was staffed; {@code null} for the food-consumption step. */
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("staffingTier")
        StaffingTier staffingTier,

    /**
     * {@code true} if this card fired only engine-builder ({@link CardTarget}) effects. All
     * resource deltas will be zero; the frontend should show a boost toast instead.
     */
    @JsonProperty("engineBoost") boolean engineBoost,
    @JsonProperty("populationDelta") double populationDelta,
    @JsonProperty("productionDelta") double productionDelta,
    @JsonProperty("foodDelta") double foodDelta,
    @JsonProperty("populationCapDelta") double populationCapDelta) {}
