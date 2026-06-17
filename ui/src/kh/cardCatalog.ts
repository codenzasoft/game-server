import { Card, ResourceTarget } from "./kingshand.ts";

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
  MARKETPLACE: {
    name: "Marketplace",
    description: "A bustling marketplace where merchants trade in coin, drawing steady gold into your settlement.",
    tags: ["COMMERCE", "GOLD"],
  },
  TRAINING_GROUNDS: {
    name: "Training Grounds",
    description: "A drilling yard where raw recruits are forged into a disciplined fighting force.",
    tags: ["MILITARY", "STRENGTH"],
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
  TRADING_POST: {
    name: "Trading Post",
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
  TRADE_ROUTES: {
    name: "Trade Routes",
    description: "Long-distance trade routes that fill the treasury with foreign gold.",
    tags: ["COMMERCE", "GOLD"],
  },
  GUARD_POST: {
    name: "Guard Post",
    description: "A fortified post where soldiers stand watch and hone their strength.",
    tags: ["MILITARY", "STRENGTH"],
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
    description: "Levy taxes for a one-time surge in gold — but the people pay the price.",
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
  GOLD_MINE: {
    name: "Gold Mine",
    description: "A rich vein of ore that pours raw gold into your coffers.",
    tags: ["INDUSTRY", "GOLD"],
  },
  ARMORY: {
    name: "Armory",
    description: "Racks of forged steel that arm your soldiers for the trials ahead.",
    tags: ["MILITARY", "STRENGTH"],
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
  FOREIGN_AID: {
    name: "Foreign Aid",
    description: "A foreign benefactor sends gold to see your settlement through the winter.",
    tags: ["COMMERCE", "GOLD"],
  },
  FORTRESS_WALL: {
    name: "Fortress Wall",
    description: "Towering walls that harden your settlement's strength against any siege.",
    tags: ["MILITARY", "STRENGTH"],
  },

  // ── Great Works ────────────────────────────────────────────────────────────
  TREASURY_CONSTRUCTION: {
    name: "Treasury (Under Construction)",
    description: "Foundations are laid for a royal treasury that will unlock the flow of gold.",
    tags: ["GREAT_WORK", "GOLD"],
  },
  TREASURY: {
    name: "Treasury",
    description: "A completed royal treasury, its vaults open and gold flowing freely into your settlement.",
    tags: ["GREAT_WORK", "GOLD"],
  },
  BARRACKS_CONSTRUCTION: {
    name: "Barracks (Under Construction)",
    description: "Timber and stone rise into barracks that will unlock the strength of a standing army.",
    tags: ["GREAT_WORK", "STRENGTH"],
  },
  BARRACKS: {
    name: "Barracks",
    description: "A completed barracks, home to a standing army that lends your settlement real strength.",
    tags: ["GREAT_WORK", "STRENGTH"],
  },
  THE_GREAT_RESERVOIR_CONSTRUCTION: {
    name: "The Great Reservoir (Under Construction)",
    description: "A vast reservoir takes shape, promising water and plenty for generations to come.",
    tags: ["GREAT_WORK", "WIN_CONDITION"],
  },
  THE_GREAT_RESERVOIR: {
    name: "The Great Reservoir",
    description: "A monumental reservoir that secures your settlement's water and food for all time.",
    tags: ["GREAT_WORK", "WIN_CONDITION"],
  },
  THE_GREAT_ROAD_CONSTRUCTION: {
    name: "The Great Road (Under Construction)",
    description: "Stone is laid mile by mile for a road that will bind your settlement to the wider world.",
    tags: ["GREAT_WORK", "WIN_CONDITION"],
  },
  THE_GREAT_ROAD: {
    name: "The Great Road",
    description: "A grand road connecting your settlement to distant lands and endless trade.",
    tags: ["GREAT_WORK", "WIN_CONDITION"],
  },
  THE_GREAT_CAPITAL_CONSTRUCTION: {
    name: "The Great Capital (Under Construction)",
    description: "Walls and towers rise around a future capital, the seat of a growing realm.",
    tags: ["GREAT_WORK", "WIN_CONDITION"],
  },
  THE_GREAT_CAPITAL: {
    name: "The Great Capital",
    description: "A magnificent capital city, the beating heart of your kingdom.",
    tags: ["GREAT_WORK", "WIN_CONDITION"],
  },
  THE_GREAT_CONQUEST_CONSTRUCTION: {
    name: "The Great Conquest (Under Construction)",
    description: "Armies muster and march as your settlement prepares for a final campaign of conquest.",
    tags: ["GREAT_WORK", "WIN_CONDITION"],
  },
  THE_GREAT_CONQUEST: {
    name: "The Great Conquest",
    description: "Your armies have triumphed — the realm bows before your settlement's conquest.",
    tags: ["GREAT_WORK", "WIN_CONDITION"],
  },
};

/**
 * The completed-card names of the four seasonal win-condition Great Works. Mirrors
 * Card.winCondition() == true on the server (TREASURY/BARRACKS unlock resources but
 * are not win conditions, so they're excluded here). Used by the win-screen UI to
 * list what the player accomplished.
 */
export const WIN_CONDITION_GREAT_WORK_NAMES = [
  "THE_GREAT_RESERVOIR",
  "THE_GREAT_ROAD",
  "THE_GREAT_CAPITAL",
  "THE_GREAT_CONQUEST",
];

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
  FORTRESS_WALL: "fortress",    // file is named fortress.png, not fortress_wall.png
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
  [ResourceTarget.STRENGTH]:       { icon: "⚔️", label: "STRENGTH",   shortLabel: "strength" },
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
