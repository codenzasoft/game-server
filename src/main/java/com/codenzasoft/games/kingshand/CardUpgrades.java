package com.codenzasoft.games.kingshand;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralised upgrade rules for King's Hand cards.
 *
 * <p>A card's upgrade level is a plain {@code int} carried on {@link Card#level()} (base = 1,
 * unbounded upward). Level is deliberately <em>not</em> encoded in {@link Card#name()} — the name
 * is the card's stable identity ({@code "FARM"}) and never changes when the card levels up. This
 * class is the single authority for how a card's effects and play requirements grow with that
 * level, so the rules can't drift between the engine, the factories, and the tests.
 *
 * <p><b>Effects</b> upgrade identically for both frequencies: each level adds the base card's
 * per-effect modifier, so a level-N card's modifier is {@code N ×} the base (before any engine
 * boosts). Combination via {@link #combineEffects} is done additively against the <em>current</em>
 * card rather than recomputed from scratch, so engine-builder boosts already baked into a table
 * card survive an upgrade (a Farm boosted to +70, upgraded with a fresh +50 hand Farm → +120).
 *
 * <p><b>Play requirements</b> are where the two {@link FrequencyType}s diverge:
 *
 * <ul>
 *   <li><b>Staff requirement</b> — both frequencies grow it by half the base per level: {@code
 *       floor(base × (level + 1) / 2)}.
 *   <li><b>Production requirement</b> — {@link FrequencyType#SINGLE} cards escalate it the same way
 *       (a one-shot has no ongoing staffing to grow, so the cost carries the scaling); {@link
 *       FrequencyType#RECURRING} cards keep the flat base production cost at every level.
 * </ul>
 *
 * <p>Requirements are always computed from the <em>base</em> value and the target level, never
 * incrementally, so repeatedly upgrading a card can never accumulate {@code floor()} rounding
 * drift.
 */
public final class CardUpgrades {

  private CardUpgrades() {}

  /**
   * Combines a leveled/possibly engine-boosted {@code current} card's effects with a fresh base
   * card's effects, paired by index. Effects present only on {@code current} (e.g. an effect the
   * base card lacks) are preserved unchanged. This is the shared effect rule for both SINGLE and
   * RECURRING cards — the two frequencies differ only in requirement scaling below.
   */
  static List<Effect> combineEffects(final Card current, final Card base) {
    final List<Effect> merged = new ArrayList<>(current.effects().size());
    for (int i = 0; i < current.effects().size(); i++) {
      final Effect mine = current.effects().get(i);
      if (i < base.effects().size()) {
        merged.add(new Effect(mine.target(), mine.modifier() + base.effects().get(i).modifier()));
      } else {
        merged.add(mine);
      }
    }
    return merged;
  }

  /** Staff requirement for a card at {@code level} given its base (level-1) staff requirement. */
  static double upgradedStaffRequirement(final double baseStaff, final int level) {
    return Math.floor(baseStaff * (level + 1) / 2.0);
  }

  /**
   * Production requirement for a card at {@code level}: SINGLE cards escalate by half the base per
   * level; RECURRING cards keep the flat base cost.
   */
  static double upgradedProductionRequirement(
      final double baseProduction, final FrequencyType frequency, final int level) {
    if (frequency == FrequencyType.SINGLE) {
      return Math.floor(baseProduction * (level + 1) / 2.0);
    }
    return baseProduction;
  }

  /**
   * Upgrades {@code current} one level by merging a freshly drawn base copy ({@code base}, level 1)
   * into it, returning the next-level card. Preserves the stable identity ({@code name}), the
   * frequency, all Great-Work metadata, and any engine boosts already present on {@code current}.
   * The played hand card {@code base} supplies the pristine base requirements the scaling is
   * computed from, which is why requirements never drift even though this is called repeatedly.
   */
  static Card merge(final Card current, final Card base) {
    final int newLevel = current.level() + 1;
    return new Card(
        current.name(),
        upgradedStaffRequirement(base.staffRequirement(), newLevel),
        upgradedProductionRequirement(base.productionRequirement(), base.frequencyType(), newLevel),
        current.frequencyType(),
        combineEffects(current, base),
        current.constructionPeriod(),
        current.completedCard(),
        current.unlockedResources(),
        current.victory(),
        current.season(),
        newLevel);
  }

  /**
   * Deterministically builds {@code base} at an arbitrary {@code level} straight from the base
   * definition, with no engine boosts. Effect modifiers scale to {@code level ×} the base; staff
   * and production requirements follow the frequency rules above. {@code level == 1} reproduces the
   * base card exactly. Useful anywhere a level-N card is needed without replaying every
   * intermediate merge (tests, tooling, previews).
   *
   * @throws IllegalArgumentException if {@code level < 1}
   */
  public static Card calculateCardAtLevel(final Card base, final int level) {
    if (level < 1) {
      throw new IllegalArgumentException("upgrade level must be >= 1, got " + level);
    }
    final List<Effect> scaled = new ArrayList<>(base.effects().size());
    for (final Effect e : base.effects()) {
      scaled.add(new Effect(e.target(), e.modifier() * level));
    }
    return new Card(
        base.name(),
        upgradedStaffRequirement(base.staffRequirement(), level),
        upgradedProductionRequirement(base.productionRequirement(), base.frequencyType(), level),
        base.frequencyType(),
        scaled,
        base.constructionPeriod(),
        base.completedCard(),
        base.unlockedResources(),
        base.victory(),
        base.season(),
        level);
  }

  private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
  private static final String[] ROMAN_SYMBOLS = {
    "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"
  };

  /**
   * Roman-numeral rendering of an upgrade level for display, e.g. {@code 4 → "IV"}, {@code 8 →
   * "VIII"}. This is a <em>display</em> helper only — card identity and game logic always use the
   * numeric {@link Card#level()}. Levels outside the classic roman range {@code [1, 3999]} fall
   * back to the plain integer so arbitrarily high levels never break formatting.
   */
  public static String roman(final int level) {
    if (level < 1 || level > 3999) {
      return Integer.toString(level);
    }
    final StringBuilder sb = new StringBuilder();
    int remaining = level;
    for (int i = 0; i < ROMAN_VALUES.length; i++) {
      while (remaining >= ROMAN_VALUES[i]) {
        sb.append(ROMAN_SYMBOLS[i]);
        remaining -= ROMAN_VALUES[i];
      }
    }
    return sb.toString();
  }

  /**
   * Human-readable display name for a card: the base display name plus a roman-numeral level suffix
   * for upgraded cards ({@code "Farm"} at level 1, {@code "Farm IV"} at level 4). The client owns
   * the authoritative display mapping; this mirrors it for server-side logs and tests.
   */
  public static String displayName(final String baseDisplayName, final int level) {
    return level <= 1 ? baseDisplayName : baseDisplayName + " " + roman(level);
  }
}
