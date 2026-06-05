import { Card } from "./kingshand.ts";

// ─── Display metadata ─────────────────────────────────────────────────────────
//
// Cards carry a stable `name` field that travels over the wire from the server.
// This module maps those names to presentation-layer metadata (display name,
// description, tags).  The `name` key is the Java constant name from
// CardCatalog.java (e.g. "FARM", "STONE_QUARRY").

export interface CardDisplayInfo {
  name: string;
  description: string;
  /** Extra category tags beyond the season tag, e.g. AGRICULTURE, INDUSTRY. */
  tags: string[];
}

const UNKNOWN_CARD: CardDisplayInfo = {
  name: "Unknown Card",
  description: "A mysterious card of unknown origin.",
  tags: [],
};

// ─── Catalog ──────────────────────────────────────────────────────────────────
//
// Keys match the `name` field on the Java Card record (CardCatalog.java).
// Add a new entry here whenever a new card constant is added on the server.

const DISPLAY_CATALOG: Record<string, CardDisplayInfo> = {
  // ── Spring ─────────────────────────────────────────────────────────────────
  FARM: {
    name: "Farm",
    description: "A working farm that reliably produces food each spring.",
    tags: ["AGRICULTURE"],
  },
  SEEDS: {
    name: "Seeds",
    description: "Scatter seeds across fallow land for a modest one-time harvest.",
    tags: ["AGRICULTURE"],
  },
  FERTILIZER: {
    name: "Fertilizer",
    description: "Enriches the soil — each food-producing card on the table yields extra food.",
    tags: ["AGRICULTURE", "ENGINE"],
  },
  MINE: {
    name: "Mine",
    description: "Dig into the hillside to extract ore every spring.",
    tags: ["INDUSTRY"],
  },
  HUNTING_CAMP: {
    name: "Hunting Camp",
    description: "A seasonal hunting camp that brings in fresh game and tools each spring.",
    tags: ["FOOD", "INDUSTRY"],
  },
  SETTLERS: {
    name: "Settlers",
    description: "A wave of settlers arrives to grow your community.",
    tags: ["POPULATION"],
  },
  LUMBER_YARD: {
    name: "Lumber Yard",
    description: "A lumber yard that multiplies the output of every production card on the table.",
    tags: ["INDUSTRY", "ENGINE"],
  },
  COMMUNITY_WELL: {
    name: "Community Well",
    description: "A shared well that keeps the village fed and steadily growing.",
    tags: ["AGRICULTURE", "POPULATION"],
  },
  IRRIGATION: {
    name: "Irrigation",
    description: "A network of channels that amplifies the output of every food-producing card on the table.",
    tags: ["AGRICULTURE", "ENGINE"],
  },

  // ── Summer ─────────────────────────────────────────────────────────────────
  BUNKHOUSE: {
    name: "Bunkhouse",
    description: "Shared worker housing that raises your settlement's population cap.",
    tags: ["HOUSING", "POPULATION"],
  },
  MANOR: {
    name: "Manor",
    description: "A grand manor that dramatically expands your settlement's population cap.",
    tags: ["HOUSING", "POPULATION"],
  },
  FARMSTEAD: {
    name: "Farmstead",
    description: "A prosperous farmstead that steadily produces both food and goods.",
    tags: ["AGRICULTURE", "INDUSTRY"],
  },
  THE_HUNT: {
    name: "The Hunt",
    description: "A summer hunt that brings in plentiful game for a one-time haul.",
    tags: ["FOOD"],
  },
  LOGGING: {
    name: "Logging",
    description: "A major logging operation that yields a large one-time surge in production.",
    tags: ["INDUSTRY"],
  },
  TRADE_ROUTES: {
    name: "Trade Routes",
    description: "Established routes that boost the output of every food and production card on the table.",
    tags: ["COMMERCE", "ENGINE"],
  },
  FISHING_VILLAGE: {
    name: "Fishing Village",
    description: "A coastal village that fishes the summer waters for steady food.",
    tags: ["FOOD"],
  },
  SCHOOL_HOUSE: {
    name: "School House",
    description: "Trained workers improve productivity across the table.",
    tags: ["INDUSTRY", "ENGINE"],
  },
  EARLY_HARVEST: {
    name: "Early Harvest",
    description: "Gather the first crops of summer before the heat peaks.",
    tags: ["AGRICULTURE"],
  },
  WINDMILL: {
    name: "Windmill",
    description: "A windmill that grinds grain and amplifies every food-producing card on the table.",
    tags: ["AGRICULTURE", "ENGINE"],
  },
  MARKETPLACE: {
    name: "Marketplace",
    description: "A busy marketplace that brings in food and production each season.",
    tags: ["COMMERCE"],
  },
  CARAVAN: {
    name: "Caravan",
    description: "Regular trade routes that bring in food and production each season.",
    tags: ["COMMERCE"],
  },
  TOWN_FAIR: {
    name: "Town Fair",
    description: "A summer fair that draws in new settlers and fills the larder.",
    tags: ["POPULATION", "FOOD"],
  },
  STONE_QUARRY: {
    name: "Stone Quarry",
    description: "A working quarry that steadily grows your production output.",
    tags: ["INDUSTRY"],
  },
  MERCHANT_GUILD: {
    name: "Merchant Guild",
    description: "A powerful guild that amplifies every food and production card on the table.",
    tags: ["COMMERCE", "ENGINE"],
  },

  // ── Fall ───────────────────────────────────────────────────────────────────
  WORKSHOP: {
    name: "Workshop",
    description: "A workshop that steadily expands your production capacity each fall.",
    tags: ["INDUSTRY"],
  },
  HARVEST: {
    name: "Harvest",
    description: "A bountiful fall harvest that fills your stores.",
    tags: ["AGRICULTURE"],
  },
  TEXTILES: {
    name: "Textiles",
    description: "A textile mill that sharpens every production card on the table each fall.",
    tags: ["INDUSTRY", "ENGINE"],
  },
  BAKERY: {
    name: "Bakery",
    description: "A bakery that turns the harvest grain into steady food output, boosting every food card on the table.",
    tags: ["FOOD", "ENGINE"],
  },
  TAX_COLLECTION: {
    name: "Tax Collection",
    description: "Levy taxes for a one-time surge in production — but the people pay the price.",
    tags: ["COMMERCE"],
  },
  STOREHOUSE: {
    name: "Storehouse",
    description: "Careful storage practices that boost the yield of every food-producing card each fall.",
    tags: ["AGRICULTURE", "ENGINE"],
  },
  BARN: {
    name: "Barn",
    description: "A barn that shelters livestock and keeps food production steady.",
    tags: ["AGRICULTURE"],
  },
  MASON_GUILD: {
    name: "Mason Guild",
    description: "Master masons whose expertise dramatically amplifies every production card on the table.",
    tags: ["INDUSTRY", "ENGINE"],
  },
  METALWORKING: {
    name: "Metalworking",
    description: "Advanced metalworking that amplifies the output of every production card on the table.",
    tags: ["INDUSTRY", "ENGINE"],
  },
  BLACKSMITH: {
    name: "Blacksmith",
    description: "A master blacksmith whose forge dramatically amplifies every production card on the table.",
    tags: ["INDUSTRY", "ENGINE"],
  },

  // ── Winter ─────────────────────────────────────────────────────────────────
  FORGE: {
    name: "Forge",
    description: "A forge that keeps production running through the long winter.",
    tags: ["INDUSTRY"],
  },
  WOODPILES: {
    name: "Woodpiles",
    description: "Stock up on firewood to endure the cold months.",
    tags: ["SURVIVAL"],
  },
  GRANARY: {
    name: "Granary",
    description: "A granary that stretches and supplements your food supplies through winter.",
    tags: ["FOOD"],
  },
  EMERGENCY_RATIONS: {
    name: "Emergency Rations",
    description: "Break open emergency stores to survive the harshest winter nights.",
    tags: ["SURVIVAL", "FOOD"],
  },
  STOCKPILE: {
    name: "Stockpile",
    description: "Draw on stockpiled reserves for a one-time boost to production and food.",
    tags: ["SURVIVAL"],
  },
  CHAPEL: {
    name: "Chapel",
    description: "A chapel that fosters hope through winter, filling bellies and drawing people in.",
    tags: ["FOOD", "POPULATION"],
  },
  PRAY: {
    name: "Pray",
    description: "Ask for divine guidance. A small blessing of food and production when all else fails.",
    tags: ["SURVIVAL"],
  },
  DESPERATE_TIMES: {
    name: "Desperate Times...",
    description: "You shall be fed — but the cost to your people is steep.",
    tags: ["SURVIVAL"],
  },
};

/** Strips a roman numeral upgrade suffix from a card name, e.g. "FARM II" → "FARM". */
function baseName(name: string): string {
  return name.replace(/ (II|III)$/, "");
}

/**
 * Returns display metadata for a card. Handles upgraded cards (e.g. "FARM II") by looking up
 * the base card and appending the suffix to the display name.
 */
export function getCardDisplayInfo(card: Card): CardDisplayInfo {
  const base = DISPLAY_CATALOG[card.name];
  if (base) return base;

  const suffix = card.name.match(/ (II|III)$/)?.[1];
  if (suffix) {
    const baseInfo = DISPLAY_CATALOG[baseName(card.name)];
    if (baseInfo) return { ...baseInfo, name: `${baseInfo.name} ${suffix}` };
  }
  return UNKNOWN_CARD;
}

/**
 * Names that don't follow the simple CARD_NAME → card_name.png convention.
 * All other cards map directly via toLowerCase().
 */
const IMAGE_NAME_OVERRIDES: Record<string, string> = {
  SCHOOL_HOUSE: "schoolhouse",  // file has no underscore
  MASON_GUILD:  "masons_guild", // file has an extra 's'
};

/** Returns the public path to a card's full-art PNG, e.g. "/img/barn.png". */
export function cardImagePath(cardName: string): string {
  const stripped = baseName(cardName);
  const base = IMAGE_NAME_OVERRIDES[stripped] ?? stripped.toLowerCase();
  return `/img/${base}.png`;
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
