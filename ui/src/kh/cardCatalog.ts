import { Card, ResourceTarget } from "./kingshand.ts";

// ─── Display metadata ─────────────────────────────────────────────────────────
//
// Cards are pure data records on the server — they carry no display name or
// description.  This module provides that presentation layer by fingerprinting
// each card (populationRequirement + productionRequirement + frequencyType +
// effect list) and looking it up against a static catalog that mirrors
// CardCatalog.java.

export interface CardDisplayInfo {
  name: string;
  icon: string;
  description: string;
  /** Extra category tags beyond the season tag, e.g. AGRICULTURE, INDUSTRY. */
  tags: string[];
}

const UNKNOWN_CARD: CardDisplayInfo = {
  name: "Unknown Card",
  icon: "🃏",
  description: "A mysterious card of unknown origin.",
  tags: [],
};

/**
 * Generates a stable string key from a {@link Card}'s fields.
 *
 * Format:
 *   {populationRequirement}:{productionRequirement}:{frequencyType}:{effect1}|{effect2}|…
 *
 * Each effect is serialised as:
 *   {target}:{modifier}:{valueType}
 *
 * where target is the ResourceTarget enum string (e.g. "FOOD") or "slot:{offset}"
 * for CardTarget effects.
 */
export function fingerprint(card: Card): string {
  const effects = card.effects
    .map((e) => {
      const target =
        typeof e.target === "string"
          ? e.target
          : `slot:${(e.target as { slotOffset: number }).slotOffset}`;
      return `${target}:${e.modifier}:${e.valueType}`;
    })
    .join("|");
  return `${card.populationRequirement}:${card.productionRequirement}:${card.frequencyType}:${effects}`;
}

// ─── Catalog ──────────────────────────────────────────────────────────────────
//
// Keys must match the fingerprint of the corresponding CardCatalog.java constant
// exactly.  If a card is added on the server, add an entry here.

const DISPLAY_CATALOG: Record<string, CardDisplayInfo> = {
  // ── Spring ─────────────────────────────────────────────────────────────────
  "2:30:RECURRING:FOOD:10:INTEGER": {
    name: "Farm",
    icon: "🌿",
    description: "A working farm that reliably produces food each spring.",
    tags: ["AGRICULTURE"],
  },
  "1:10:SINGLE:FOOD:8:INTEGER": {
    name: "Seeds",
    icon: "🌱",
    description: "Scatter seeds across fallow land for a modest one-time harvest.",
    tags: ["AGRICULTURE"],
  },
  "1:15:RECURRING:FOOD:10:PERCENTAGE": {
    name: "Fertilizer",
    icon: "🪣",
    description: "Enrich the soil each spring to boost all food output.",
    tags: ["AGRICULTURE"],
  },
  "5:60:RECURRING:PRODUCTION:15:INTEGER": {
    name: "Mine",
    icon: "⛏️",
    description: "Dig into the hillside to extract ore every spring.",
    tags: ["INDUSTRY"],
  },

  // ── Summer ─────────────────────────────────────────────────────────────────
  "0:50:RECURRING:POPULATION:2:INTEGER|POPULATION:2:INTEGER": {
    name: "Farmstead",
    icon: "🏡",
    description: "A prosperous farmstead that grows your population.",
    tags: ["AGRICULTURE"],
  },
  "15:20:SINGLE:FOOD:30:INTEGER": {
    name: "Hunting",
    icon: "🏹",
    description: "A summer hunt that brings in plentiful game.",
    tags: ["FOOD"],
  },
  "20:100:RECURRING:POPULATION:3:INTEGER": {
    name: "Settlement",
    icon: "🏘️",
    description: "Expand your settlement to house more people.",
    tags: ["POPULATION"],
  },
  "3:25:RECURRING:PRODUCTION:10:INTEGER": {
    name: "Logging",
    icon: "🪵",
    description: "Harvest timber to boost production each summer.",
    tags: ["INDUSTRY"],
  },
  "4:80:RECURRING:PRODUCTION:5:PERCENTAGE|FOOD:5:PERCENTAGE": {
    name: "Trade Routes",
    icon: "🚢",
    description: "Establish routes that grow both production and food.",
    tags: ["COMMERCE"],
  },
  "3:40:RECURRING:FOOD:15:INTEGER": {
    name: "Fishing Village",
    icon: "🎣",
    description: "A coastal village that fishes the summer waters.",
    tags: ["FOOD"],
  },
  "2:50:RECURRING:POPULATION:1:INTEGER": {
    name: "School House",
    icon: "🏫",
    description: "A school that slowly grows your population over time.",
    tags: ["POPULATION"],
  },

  // ── Fall ───────────────────────────────────────────────────────────────────
  "5:40:RECURRING:PRODUCTION:10:PERCENTAGE": {
    name: "Workshop",
    icon: "🔧",
    description: "A workshop that expands your production capacity each fall.",
    tags: ["INDUSTRY"],
  },
  "8:120:RECURRING:POPULATION:2:INTEGER|PRODUCTION:20:INTEGER": {
    name: "Manor",
    icon: "🏰",
    description: "A grand manor that attracts settlers and skilled workers.",
    tags: ["POPULATION", "INDUSTRY"],
  },
  "2:10:SINGLE:FOOD:40:INTEGER": {
    name: "Harvest",
    icon: "🌾",
    description: "A bountiful fall harvest that fills your stores.",
    tags: ["AGRICULTURE"],
  },
  "4:60:RECURRING:PRODUCTION:20:PERCENTAGE": {
    name: "Textiles",
    icon: "🧵",
    description: "A textile mill that dramatically increases production.",
    tags: ["INDUSTRY"],
  },

  // ── Winter ─────────────────────────────────────────────────────────────────
  "3:50:RECURRING:PRODUCTION:15:INTEGER": {
    name: "Forge",
    icon: "🔥",
    description: "A forge that keeps production running through winter.",
    tags: ["INDUSTRY"],
  },
  "1:10:SINGLE:PRODUCTION:25:INTEGER": {
    name: "Woodpiles",
    icon: "🪵",
    description: "Stock up on firewood to endure the cold months.",
    tags: ["SURVIVAL"],
  },
  "2:40:RECURRING:FOOD:10:PERCENTAGE|FOOD:20:INTEGER": {
    name: "Granary",
    icon: "🏪",
    description: "A granary that stretches your food supplies through winter.",
    tags: ["FOOD"],
  },
};

/** Returns display metadata for a card, falling back to a placeholder for unknown cards. */
export function getCardDisplayInfo(card: Card): CardDisplayInfo {
  return DISPLAY_CATALOG[fingerprint(card)] ?? UNKNOWN_CARD;
}

/** Formats an effect modifier as a signed string, e.g. "+10" or "+10%". */
export function formatModifier(
  modifier: number,
  valueType: "INTEGER" | "PERCENTAGE" | string,
): string {
  const sign = modifier >= 0 ? "+" : "";
  return valueType === "PERCENTAGE"
    ? `${sign}${modifier}%`
    : `${sign}${modifier}`;
}

/** Returns the emoji icon for a ResourceTarget, or an empty string for CardTarget. */
export function targetEmoji(target: unknown): string {
  if (target === ResourceTarget.FOOD) return "🌾";
  if (target === ResourceTarget.PRODUCTION) return "⚒️";
  if (target === ResourceTarget.POPULATION) return "👥";
  return "";
}
