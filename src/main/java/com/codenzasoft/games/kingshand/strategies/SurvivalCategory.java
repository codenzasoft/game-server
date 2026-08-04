package com.codenzasoft.games.kingshand.strategies;

/**
 * How safe a kingdom's resource position is, driving how aggressively the bot is willing to grow or
 * attack. Ordered from most to least precarious.
 *
 * <ul>
 *   <li>{@link #CRITICAL} — starvation is imminent; prioritise immediate food/survival above all.
 *   <li>{@link #UNSAFE} — survives the next upkeep but has no recovery margin; build recurring food
 *       and production, avoid new upkeep obligations.
 *   <li>{@link #STABLE} — enough food and production for several decisions; balance development and
 *       opponent pressure.
 *   <li>{@link #STRONG} — sustainable recurring generation; free to pursue growth, Great Works, and
 *       offense to close out the game.
 * </ul>
 */
public enum SurvivalCategory {
  CRITICAL,
  UNSAFE,
  STABLE,
  STRONG
}
