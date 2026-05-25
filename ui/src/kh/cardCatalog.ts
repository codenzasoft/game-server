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
  "3:20:RECURRING:FOOD:12:INTEGER": {
    name: "Hunting Camp",
    icon: "🏕️",
    description: "A seasonal hunting camp that brings in fresh game each spring.",
    tags: ["FOOD"],
  },
  "5:45:SINGLE:POPULATION:3:INTEGER": {
    name: "Settlers",
    icon: "🧳",
    description: "A wave of settlers arrives to grow your community.",
    tags: ["POPULATION"],
  },
  "2:25:RECURRING:PRODUCTION:8:INTEGER": {
    name: "Lumber Yard",
    icon: "🪓",
    description: "A lumber yard that harvests timber to steadily boost production.",
    tags: ["INDUSTRY"],
  },
  "1:20:RECURRING:FOOD:5:INTEGER|POPULATION:1:INTEGER": {
    name: "Community Well",
    icon: "⛲",
    description: "A shared well that keeps the village healthy and slowly growing.",
    tags: ["AGRICULTURE", "POPULATION"],
  },
  "3:35:RECURRING:FOOD:15:PERCENTAGE": {
    name: "Irrigation",
    icon: "💧",
    description: "Channel water to the fields, dramatically improving crop yields.",
    tags: ["AGRICULTURE"],
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
  "2:15:SINGLE:FOOD:25:INTEGER": {
    name: "Early Harvest",
    icon: "🌽",
    description: "Gather the first crops of summer before the heat peaks.",
    tags: ["AGRICULTURE"],
  },
  "3:30:RECURRING:PRODUCTION:12:INTEGER": {
    name: "Blacksmith",
    icon: "🔨",
    description: "A skilled blacksmith who crafts tools and steadily boosts production.",
    tags: ["INDUSTRY"],
  },
  "2:45:RECURRING:FOOD:10:INTEGER|PRODUCTION:5:INTEGER": {
    name: "Windmill",
    icon: "🌬️",
    description: "A windmill that grinds grain and keeps production turning.",
    tags: ["AGRICULTURE", "INDUSTRY"],
  },
  "4:70:RECURRING:PRODUCTION:10:PERCENTAGE|FOOD:5:INTEGER": {
    name: "Marketplace",
    icon: "🏪",
    description: "A bustling marketplace that drives commerce and brings in provisions.",
    tags: ["COMMERCE"],
  },
  "6:55:RECURRING:PRODUCTION:15:PERCENTAGE": {
    name: "Skilled Labor",
    icon: "👷",
    description: "Recruit skilled workers to significantly boost production output.",
    tags: ["INDUSTRY"],
  },
  "3:60:RECURRING:PRODUCTION:10:INTEGER|FOOD:10:INTEGER": {
    name: "Caravan",
    icon: "🐪",
    description: "A trade caravan that returns each summer with goods and provisions.",
    tags: ["COMMERCE"],
  },
  "5:65:SINGLE:POPULATION:4:INTEGER|FOOD:20:INTEGER": {
    name: "Town Fair",
    icon: "🎠",
    description: "A summer fair that draws in new settlers and fills the larder.",
    tags: ["POPULATION", "FOOD"],
  },
  "4:50:RECURRING:PRODUCTION:15:INTEGER": {
    name: "Brickworks",
    icon: "🧱",
    description: "A brickworks that manufactures building materials each summer.",
    tags: ["INDUSTRY"],
  },
  "6:90:RECURRING:PRODUCTION:10:PERCENTAGE|FOOD:10:PERCENTAGE": {
    name: "Merchant Guild",
    icon: "💰",
    description: "An established guild that expands both production and food stores.",
    tags: ["COMMERCE"],
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
  "2:35:RECURRING:FOOD:12:INTEGER": {
    name: "Bakery",
    icon: "🍞",
    description: "A bakery that turns the harvest grain into steady food production.",
    tags: ["FOOD"],
  },
  "4:0:SINGLE:PRODUCTION:40:INTEGER": {
    name: "Tax Collection",
    icon: "📜",
    description: "Collect taxes from the populace for a one-time surge in production.",
    tags: ["COMMERCE"],
  },
  "3:45:RECURRING:FOOD:15:PERCENTAGE": {
    name: "Storehouse",
    icon: "📦",
    description: "A storehouse that stretches your food reserves each fall.",
    tags: ["AGRICULTURE"],
  },
  "2:30:RECURRING:FOOD:15:INTEGER": {
    name: "Barn",
    icon: "🐄",
    description: "A barn that shelters livestock and keeps food production steady.",
    tags: ["AGRICULTURE"],
  },
  "5:80:RECURRING:PRODUCTION:25:INTEGER": {
    name: "Mason Guild",
    icon: "🏗️",
    description: "A guild of master masons who drive substantial production each fall.",
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
  "1:5:SINGLE:FOOD:25:INTEGER": {
    name: "Emergency Rations",
    icon: "🥫",
    description: "Break open emergency stores to survive the harshest winter nights.",
    tags: ["SURVIVAL", "FOOD"],
  },
  "1:20:SINGLE:PRODUCTION:20:INTEGER|FOOD:15:INTEGER": {
    name: "Stockpile",
    icon: "🗄️",
    description: "Draw on stockpiled reserves for a one-time boost to goods and food.",
    tags: ["SURVIVAL"],
  },
  "3:40:RECURRING:POPULATION:2:INTEGER": {
    name: "Chapel",
    icon: "⛪",
    description: "A chapel that fosters community spirit and draws people to the settlement.",
    tags: ["POPULATION"],
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
