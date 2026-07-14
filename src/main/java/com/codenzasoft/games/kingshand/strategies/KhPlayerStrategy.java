package com.codenzasoft.games.kingshand.strategies;

import com.codenzasoft.games.kingshand.GameEngine;

/**
 * Drives one seat of a King's Hand match — the KH counterpart of Hearts' {@code AdaptiveStrategy}.
 * Registered per bot seat via {@code GameEngine.Builder#withStrategy} (see {@code
 * GameService.buildKhGame}); the engine invokes it whenever the seat still owes an action in the
 * current phase (see {@code GameEngine#driveBots()}).
 *
 * <p>Contract: {@link #act} must complete the seat's current phase by calling one of the engine's
 * phase-completing actions ({@code pickCard}/{@code skipAdd}, {@code endAndRun}, {@code
 * hireAdvisor}/{@code skipCouncilAdd}, {@code acceptCouncilProposal}/{@code passCouncilProposal}).
 * Strategies go through the exact same seat-aware action methods as humans — no gameplay may branch
 * on "is a bot" beyond who triggers the action. If a strategy fails to submit, the engine
 * force-completes the phase with the default action so the readiness barrier never stalls on a bot.
 */
public interface KhPlayerStrategy {

  /**
   * Takes {@code seat}'s action(s) for the current phase. The engine has already pointed its active
   * seat at {@code seat} before invoking this.
   *
   * @param engine the engine to act through
   * @param seat the seat this strategy is driving
   */
  void act(GameEngine engine, int seat);
}
