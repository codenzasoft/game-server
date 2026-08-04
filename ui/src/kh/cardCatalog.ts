import { Card, ResourceTarget } from "./kingshand.ts";
import { CardDisplayInfo, displayInfoForName } from "./cardDisplay.ts";

// ─── Display metadata ─────────────────────────────────────────────────────────
//
// Cards carry a stable `name` (identity) and a numeric `level` (how upgraded),
// both of which travel over the wire from the server. The presentation-layer
// mapping — display name, description, tags, artwork path, roman-numeral level —
// lives in the dependency-free `cardDisplay.ts` so it can be unit-tested without
// pulling in the Pixi/STOMP-heavy `kingshand.ts`. This module is the thin,
// `Card`-typed adapter over it, plus the resource-display helpers that need the
// `ResourceTarget` enum.

// Re-export the presentation surface so existing importers of ./cardCatalog keep working.
export {
  cardImagePath,
  cardArtAttrs,
  CARD_MISSING_ART,
  formatModifier,
  roman,
  WIN_CONDITION_GREAT_WORK_NAMES,
} from "./cardDisplay.ts";
export type { CardDisplayInfo } from "./cardDisplay.ts";

/**
 * Returns display metadata for a card. Resolves by the card's stable `name` + numeric `level`
 * (e.g. FARM at level 4 → "Farm IV"), tolerating an absent level (treated as 1) and legacy
 * level-in-name payloads. A missing PNG never reaches here — artwork is resolved separately by
 * {@link cardImagePath}/{@link cardArtAttrs}.
 */
export function getCardDisplayInfo(card: Card): CardDisplayInfo {
  return displayInfoForName(card.name, card.level ?? 1);
}

// ─── Resource display metadata ─────────────────────────────────────────────────
//
// Single source of truth for how each ResourceTarget renders: its icon, its
// effect-row label (matches the legacy raw-enum-string convention used in card
// effect text), and its short label (used in engine-effect scope text, e.g.
// "next food card"). Every resource bar, effect description, and resolution
// indicator across ui/src/kh should go through these instead of hand-rolling a
// new icon ternary — historically those ternaries were duplicated ~10+ times
// and every copy silently dropped GOLD/STRENGTH (no icon, or the wrong icon).

const RESOURCE_DISPLAY: Record<ResourceTarget, { icon: string; label: string; shortLabel: string }> = {
  [ResourceTarget.POPULATION]:     { icon: "👥",  label: "POPULATION", shortLabel: "population" },
  [ResourceTarget.PRODUCTION]:     { icon: "⚒️", label: "PRODUCTION", shortLabel: "prod" },
  [ResourceTarget.FOOD]:           { icon: "🌾",  label: "FOOD",       shortLabel: "food" },
  [ResourceTarget.POPULATION_CAP]: { icon: "🏠",  label: "POP CAP",    shortLabel: "population_cap" },
  [ResourceTarget.GOLD]:           { icon: "💰",  label: "GOLD",       shortLabel: "gold" },
  [ResourceTarget.STRENGTH]:       { icon: "🛡", label: "DEFENSE",    shortLabel: "defense" },
};

/** Icon for a resource target, e.g. ResourceTarget.GOLD → "💰". Falls back to "❓" for unrecognized values. */
export function resourceIcon(target: ResourceTarget | string): string {
  return RESOURCE_DISPLAY[target as ResourceTarget]?.icon ?? "❓";
}

/** Effect-row label for a resource target, e.g. ResourceTarget.POPULATION_CAP → "POP CAP". Falls back to the raw string. */
export function resourceLabel(target: ResourceTarget | string): string {
  return RESOURCE_DISPLAY[target as ResourceTarget]?.label ?? String(target);
}

/** Lowercase short label used in engine-effect scope text, e.g. "next food card". */
export function resourceShortLabel(target: ResourceTarget | string): string {
  return RESOURCE_DISPLAY[target as ResourceTarget]?.shortLabel ?? String(target).toLowerCase();
}
