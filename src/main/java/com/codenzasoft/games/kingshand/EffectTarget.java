package com.codenzasoft.games.kingshand;

/**
 * What an {@link Effect} acts on. The prototype supports two target kinds:
 *
 * <ul>
 *   <li>{@link ResourceTarget} — global resources (population, production, food).
 *   <li>{@link CardTarget} — another card on the same season's table, identified by slot offset.
 * </ul>
 *
 * <p>The hierarchy is sealed so callers can exhaustively pattern-match without leaving room for an
 * unhandled target kind to silently slip through.
 *
 * <p>{@link OpponentResourceTarget} is the sabotage branch: it acts on the opponent's kingdom and
 * is resolved against a pre-resolution snapshot (see {@code GameEngine}'s PLAY resolution).
 */
public sealed interface EffectTarget permits ResourceTarget, CardTarget, OpponentResourceTarget {}
