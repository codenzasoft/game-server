import { Card, ResourceTarget } from "./kingshand.ts";

// ─── Display metadata ─────────────────────────────────────────────────────────
//
// Cards carry a stable `name` field that travels over the wire from the server.
// This module maps those names to presentation-layer metadata (display name,
// icon, description, tags).  The `name` key is the Java constant name from
// CardCatalog.java (e.g. "FARM", "STONE_QUARRY").

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

// ─── Catalog ──────────────────────────────────────────────────────────────────
//
// Keys match the `name` field on the Java Card record (CardCatalog.java).
// Add a new entry here whenever a new card constant is added on the server.

const DISPLAY_CATALOG: Record<string, CardDisplayInfo> = {
  // ── Spring ─────────────────────────────────────────────────────────────────
  FARM: {
    name: "Farm",
    icon: "🌿",
    description: "A working farm that reliably produces food each spring.",
    tags: ["AGRICULTURE"],
  },
  SEEDS: {
    name: "Seeds",
    icon: "🌱",
    description: "Scatter seeds across fallow land for a modest one-time harvest.",
    tags: ["AGRICULTURE"],
  },
  FERTILIZER: {
    name: "Fertilizer",
    icon: "🪣",
    description: "Enrich the soil each spring to boost all food output.",
    tags: ["AGRICULTURE"],
  },
  MINE: {
    name: "Mine",
    icon: "⛏️",
    description: "Dig into the hillside to extract ore every spring.",
    tags: ["INDUSTRY"],
  },
  HUNTING_CAMP: {
    name: "Hunting Camp",
    icon: "🏕️",
    description: "A seasonal hunting camp that brings in fresh game and tools each spring.",
    tags: ["FOOD", "INDUSTRY"],
  },
  SETTLERS: {
    name: "Settlers",
    icon: "🧳",
    description: "A wave of settlers arrives to grow your community.",
    tags: ["POPULATION"],
  },
  LUMBER_YARD: {
    name: "Lumber Yard",
    icon: "🪓",
    description: "A lumber yard that harvests timber and compounds your production each spring.",
    tags: ["INDUSTRY"],
  },
  COMMUNITY_WELL: {
    name: "Community Well",
    icon: "⛲",
    description: "A shared well that keeps the village fed and steadily growing.",
    tags: ["AGRICULTURE", "POPULATION"],
  },
  IRRIGATION: {
    name: "Irrigation",
    icon: "💧",
    description: "Channel water to the fields, dramatically boosting crop yields each spring.",
    tags: ["AGRICULTURE"],
  },

  // ── Summer ─────────────────────────────────────────────────────────────────
  FARMSTEAD: {
    name: "Farmstead",
    icon: "🏡",
    description: "A prosperous farmstead that steadily produces both food and goods.",
    tags: ["AGRICULTURE", "INDUSTRY"],
  },
  THE_HUNT: {
    name: "The Hunt",
    icon: "🏹",
    description: "A summer hunt that brings in plentiful game for a one-time haul.",
    tags: ["FOOD"],
  },
  LOGGING: {
    name: "Logging",
    icon: "🪵",
    description: "A major logging operation that yields a large one-time surge in production.",
    tags: ["INDUSTRY"],
  },
  TRADE_ROUTES: {
    name: "Trade Routes",
    icon: "🚢",
    description: "Establish routes that grow both production and food every summer.",
    tags: ["COMMERCE"],
  },
  FISHING_VILLAGE: {
    name: "Fishing Village",
    icon: "🎣",
    description: "A coastal village that fishes the summer waters for steady food.",
    tags: ["FOOD"],
  },
  SCHOOL_HOUSE: {
    name: "School House",
    icon: "🏫",
    description: "A school that trains workers and improves your kingdom's output.",
    tags: ["INDUSTRY", "AGRICULTURE"],
  },
  EARLY_HARVEST: {
    name: "Early Harvest",
    icon: "🌽",
    description: "Gather the first crops of summer before the heat peaks.",
    tags: ["AGRICULTURE"],
  },
  WINDMILL: {
    name: "Windmill",
    icon: "🌬️",
    description: "A windmill that grinds grain and keeps production turning each summer.",
    tags: ["AGRICULTURE", "INDUSTRY"],
  },
  MARKETPLACE: {
    name: "Marketplace",
    icon: "🏪",
    description: "A bustling marketplace that drives commerce and brings in provisions.",
    tags: ["COMMERCE"],
  },
  CARAVAN: {
    name: "Caravan",
    icon: "🐪",
    description: "A trade caravan that returns each summer with goods and extra provisions.",
    tags: ["COMMERCE"],
  },
  TOWN_FAIR: {
    name: "Town Fair",
    icon: "🎠",
    description: "A summer fair that draws in new settlers and fills the larder.",
    tags: ["POPULATION", "FOOD"],
  },
  STONE_QUARRY: {
    name: "Stone Quarry",
    icon: "🪨",
    description: "A quarry that extracts stone each summer, boosting and compounding production.",
    tags: ["INDUSTRY"],
  },
  MERCHANT_GUILD: {
    name: "Merchant Guild",
    icon: "💰",
    description: "An established guild that expands both production and food stores.",
    tags: ["COMMERCE"],
  },

  // ── Fall ───────────────────────────────────────────────────────────────────
  WORKSHOP: {
    name: "Workshop",
    icon: "🔧",
    description: "A workshop that steadily expands your production capacity each fall.",
    tags: ["INDUSTRY"],
  },
  MANOR: {
    name: "Manor",
    icon: "🏰",
    description: "A grand manor that attracts settlers and skilled workers each fall.",
    tags: ["POPULATION", "INDUSTRY"],
  },
  HARVEST: {
    name: "Harvest",
    icon: "🌾",
    description: "A bountiful fall harvest that fills your stores.",
    tags: ["AGRICULTURE"],
  },
  TEXTILES: {
    name: "Textiles",
    icon: "🧵",
    description: "A textile mill that compounds and boosts your production output.",
    tags: ["INDUSTRY"],
  },
  BAKERY: {
    name: "Bakery",
    icon: "🍞",
    description: "A bakery that turns the harvest grain into steady and growing food output.",
    tags: ["FOOD"],
  },
  TAX_COLLECTION: {
    name: "Tax Collection",
    icon: "📜",
    description: "Levy taxes for a one-time surge in production — but the people pay the price.",
    tags: ["COMMERCE"],
  },
  STOREHOUSE: {
    name: "Storehouse",
    icon: "📦",
    description: "A storehouse that stretches your food reserves each fall.",
    tags: ["AGRICULTURE"],
  },
  BARN: {
    name: "Barn",
    icon: "🐄",
    description: "A barn that shelters livestock and keeps food production steady.",
    tags: ["AGRICULTURE"],
  },
  MASON_GUILD: {
    name: "Mason Guild",
    icon: "🏗️",
    description: "A guild of master masons who drive substantial and compounding production.",
    tags: ["INDUSTRY"],
  },
  METALWORKING: {
    name: "Metalworking",
    icon: "⚙️",
    description: "A metalworking operation that compounds your production output each fall.",
    tags: ["INDUSTRY"],
  },
  BLACKSMITH: {
    name: "Blacksmith",
    icon: "🔨",
    description: "A master blacksmith's forge that dramatically accelerates production each fall.",
    tags: ["INDUSTRY"],
  },

  // ── Winter ─────────────────────────────────────────────────────────────────
  FORGE: {
    name: "Forge",
    icon: "🔥",
    description: "A forge that keeps production running through the long winter.",
    tags: ["INDUSTRY"],
  },
  WOODPILES: {
    name: "Woodpiles",
    icon: "🪵",
    description: "Stock up on firewood to endure the cold months.",
    tags: ["SURVIVAL"],
  },
  GRANARY: {
    name: "Granary",
    icon: "🌾",
    description: "A granary that stretches and supplements your food supplies through winter.",
    tags: ["FOOD"],
  },
  EMERGENCY_RATIONS: {
    name: "Emergency Rations",
    icon: "🥫",
    description: "Break open emergency stores to survive the harshest winter nights.",
    tags: ["SURVIVAL", "FOOD"],
  },
  STOCKPILE: {
    name: "Stockpile",
    icon: "🗄️",
    description: "Draw on stockpiled reserves for a one-time boost to production and food.",
    tags: ["SURVIVAL"],
  },
  CHAPEL: {
    name: "Chapel",
    icon: "⛪",
    description: "A chapel that fosters hope through winter, filling bellies and drawing people in.",
    tags: ["FOOD", "POPULATION"],
  },
  PRAY: {
    name: "Pray",
    icon: "🙏",
    description: "Ask for divine guidance. A small blessing of food and production when all else fails.",
    tags: ["SURVIVAL"],
  },
  DESPERATE_TIMES: {
    name: "Desperate Times...",
    icon: "⚠️",
    description: "You shall be fed — but the cost to your people is steep.",
    tags: ["SURVIVAL"],
  },
};

/** Returns display metadata for a card, falling back to a placeholder for unknown cards. */
export function getCardDisplayInfo(card: Card): CardDisplayInfo {
  return DISPLAY_CATALOG[card.name] ?? UNKNOWN_CARD;
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
  const base = IMAGE_NAME_OVERRIDES[cardName] ?? cardName.toLowerCase();
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

/** Returns the emoji icon for a ResourceTarget, or an empty string for CardTarget. */
export function targetEmoji(target: unknown): string {
  if (target === ResourceTarget.FOOD) return "🌾";
  if (target === ResourceTarget.PRODUCTION) return "⚒️";
  if (target === ResourceTarget.POPULATION) return "👥";
  return "";
}
