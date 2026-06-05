package com.codenzasoft.games.kingshand;

/**
 * An engine-builder effect target. When a card carrying a {@code CardTarget} effect fires it boosts
 * downstream cards on the current season's table that produce the specified resource.
 *
 * <p>Two scopes:
 *
 * <ul>
 *   <li>{@code adjacentOnly = true} — applies the bonus only to the <em>first</em> card to the
 *       right that produces {@code targetResource}. Recurring; modifier 4–8.
 *   <li>{@code adjacentOnly = false} — applies the bonus to <em>every</em> card to the right that
 *       produces {@code targetResource}. Recurring modifier 1–3 or single-use modifier 4–8.
 * </ul>
 *
 * @param targetResource the resource type to boost on downstream cards
 * @param adjacentOnly {@code true} to boost only the next matching card; {@code false} for all
 */
public record CardTarget(ResourceTarget targetResource, boolean adjacentOnly)
    implements EffectTarget {}
