package com.codenzasoft.games.kingshand.strategies;

import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.PlayerState;

/**
 * Historically produced a deep copy of a {@link GameState} so the search could simulate actions on
 * a throwaway copy without mutating the live match.
 *
 * <p><b>Now a no-op.</b> {@link GameState}, {@link PlayerState}, {@link
 * com.codenzasoft.games.kingshand.SeatedAdvisor}, and their contents are immutable records: the
 * engine produces new instances via {@code with*} rather than mutating in place, so a "copy" and
 * the original can be the same reference. {@code deepCopy} therefore returns its argument
 * unchanged.
 *
 * <p>TODO: immutable — copy no longer needed. Once the search code paths are confirmed copy-free,
 * delete this class and drop the {@code deepCopy} calls in {@code KhSimulator}.
 */
public final class KhGameStateCloner {

  private KhGameStateCloner() {}

  /** Returns {@code src} unchanged — immutable state needs no copy. */
  public static GameState deepCopy(final GameState src) {
    return src;
  }

  /** Returns {@code p} unchanged — immutable state needs no copy. */
  public static PlayerState deepCopy(final PlayerState p) {
    return p;
  }
}
