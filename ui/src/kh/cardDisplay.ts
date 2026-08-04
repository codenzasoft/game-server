// ─── Card display + artwork resolution (pure, dependency-free) ─────────────────
//
// This module maps a card's *stable identity* — its `name` (the Java constant,
// e.g. "FARM") plus its numeric upgrade `level` — to presentation metadata
// (display name, description, tags) and to an artwork path.
//
// It is deliberately free of any import from kingshand.ts (which touches
// `window` at module load and pulls in Pixi/STOMP), so the identity/artwork
// rules can be unit-tested in a plain Node/vitest environment.
//
// Key principle: **identity and artwork are resolved separately.** A card whose
// PNG is missing still resolves to the correct name/description and simply falls
// back to a placeholder image — a missing texture never turns a known card into
// "Unknown Card".

export interface CardDisplayInfo {
  name: string;
  description: string;
  /** Extra category tags beyond the season tag, e.g. AGRICULTURE, INDUSTRY. */
  tags: string[];
}

export const UNKNOWN_CARD: CardDisplayInfo = {
  name: "Unknown Card",
  description: "A mysterious card of unknown origin.",
  tags: [],
};

/** Public path to the intentional "art coming soon" placeholder used when a card PNG is absent. */
export const CARD_MISSING_ART = "/img/card-missing.svg";

// ─── Catalog ──────────────────────────────────────────────────────────────────
//
// Keys match the stable `name` field on the Java Card record (CardCatalog.java).
// Add a new entry here whenever a new card constant is added on the server.

export const DISPLAY_CATALOG: Record<string, CardDisplayInfo> = {
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

  // ── Sabotage (cross-kingdom; requires STRENGTH) ────────────────────────────
  SABOTEURS: {
    name: "Saboteurs",
    description: "Agents slip into the rival kingdom and wreck their workshops — drains the opponent's production.",
    tags: ["MILITARY", "SABOTAGE"],
  },
  CROP_RAID: {
    name: "Crop Raid",
    description: "Raiders torch the rival kingdom's granaries — burns the opponent's food stores.",
    tags: ["MILITARY", "SABOTAGE"],
  },
  WINTER_RAIDERS: {
    name: "Winter Raiders",
    description: "Hardened raiders harry the rival kingdom's economy every winter they remain in your service.",
    tags: ["MILITARY", "SABOTAGE"],
  },

  // ── Damage (direct assault on the rival's population; requires STRENGTH) ────
  RAIDERS_STRIKE: {
    name: "Raiders' Strike",
    description: "A quick raid on the rival kingdom's people.",
    tags: ["MILITARY", "COMBAT"],
  },
  WARBAND: {
    name: "Warband",
    description: "A war party strikes deeper into rival lands, cutting down their population.",
    tags: ["MILITARY", "COMBAT"],
  },
  SIEGE: {
    name: "Siege",
    description: "Siege engines batter the rival's walls and homes every season they hold.",
    tags: ["MILITARY", "COMBAT"],
  },
  ONSLAUGHT: {
    name: "Onslaught",
    description: "An all-out onslaught that grinds down the rival kingdom's population each winter.",
    tags: ["MILITARY", "COMBAT"],
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
 * Card.victory() == true on the server (TREASURY/BARRACKS unlock resources but
 * are not win conditions, so they're excluded here). Used by the win-screen UI.
 */
export const WIN_CONDITION_GREAT_WORK_NAMES = [
  "THE_GREAT_RESERVOIR",
  "THE_GREAT_ROAD",
  "THE_GREAT_CAPITAL",
  "THE_GREAT_CONQUEST",
];

// ─── Roman numerals (display only) ──────────────────────────────────────────────

const ROMAN_UNITS: [number, string][] = [
  [1000, "M"], [900, "CM"], [500, "D"], [400, "CD"], [100, "C"], [90, "XC"],
  [50, "L"], [40, "XL"], [10, "X"], [9, "IX"], [5, "V"], [4, "IV"], [1, "I"],
];

/**
 * Roman-numeral rendering of an upgrade level for display, e.g. 4 → "IV", 8 → "VIII".
 * Levels outside the classic roman range [1, 3999] fall back to the plain integer, so an
 * arbitrarily high level never breaks formatting. This is a display helper only — identity
 * and game logic use the numeric level.
 */
export function roman(level: number): string {
  if (!Number.isFinite(level) || level < 1 || level > 3999) return String(level);
  let remaining = Math.floor(level);
  let out = "";
  for (const [value, symbol] of ROMAN_UNITS) {
    while (remaining >= value) {
      out += symbol;
      remaining -= value;
    }
  }
  return out;
}

/** Parses a roman-numeral token to its integer value, or null if it isn't a valid numeral. */
export function romanToInt(token: string): number | null {
  if (!/^[MDCLXVI]+$/.test(token)) return null;
  const values: Record<string, number> = { I: 1, V: 5, X: 10, L: 50, C: 100, D: 500, M: 1000 };
  let total = 0;
  for (let i = 0; i < token.length; i++) {
    const cur = values[token[i]];
    const next = values[token[i + 1]];
    total += next && cur < next ? -cur : cur;
  }
  return total > 0 ? total : null;
}

// ─── Identity + artwork resolution ─────────────────────────────────────────────

interface CardIdentity {
  /** Stable base id (a DISPLAY_CATALOG key), e.g. "FARM". */
  base: string;
  /** Upgrade level (>= 1). */
  level: number;
}

/**
 * Resolves a wire `name` + `level` to a stable base id and level.
 *
 * The modern wire sends a stable id ("FARM") plus a numeric `level`. For backward compatibility
 * with pre-fix payloads that baked the level into the name (e.g. "FARM II", or even the buggy
 * "FARM III II"), a name that isn't a known catalog key is retried by stripping trailing
 * roman-numeral tokens until a known base id is found. Returns null only if the base id is genuinely
 * unrecognised — a missing PNG is *not* an unknown identity.
 */
export function resolveIdentity(name: string, level: number = 1): CardIdentity | null {
  if (DISPLAY_CATALOG[name]) {
    return { base: name, level: Math.max(1, Math.floor(level || 1)) };
  }
  // Legacy: name may carry one or more roman-numeral suffix tokens.
  const parts = name.split(" ");
  if (parts.length > 1) {
    let legacyLevel = 1;
    while (parts.length > 1) {
      const value = romanToInt(parts[parts.length - 1]);
      if (value === null) break;
      legacyLevel = value; // rightmost roman token carries the (authoritative) legacy level
      parts.pop();
    }
    const base = parts.join(" ");
    if (DISPLAY_CATALOG[base]) {
      return { base, level: Math.max(1, legacyLevel) };
    }
  }
  return null;
}

/**
 * Returns display metadata for a card by its stable `name` and numeric `level`. Upgraded cards get
 * a roman-numeral suffix on the display name ("Farm IV"). Unknown *identities* fall through to
 * {@link UNKNOWN_CARD}; a known card with missing art does NOT — art is resolved separately.
 */
export function displayInfoForName(name: string, level: number = 1): CardDisplayInfo {
  const id = resolveIdentity(name, level);
  if (!id) return UNKNOWN_CARD;
  const base = DISPLAY_CATALOG[id.base];
  return id.level > 1 ? { ...base, name: `${base.name} ${roman(id.level)}` } : base;
}

/**
 * Names that don't follow the simple CARD_NAME → card_name.png convention.
 * All other cards map directly via toLowerCase().
 */
export const IMAGE_NAME_OVERRIDES: Record<string, string> = {
  SCHOOL_HOUSE: "schoolhouse", // file has no underscore
  MASON_GUILD: "masons_guild", // file has an extra 's'
  FORTRESS_WALL: "fortress", // file is named fortress.png, not fortress_wall.png
  // Sabotage cards reuse military art until dedicated pieces are drawn.
  SABOTEURS: "guard_post",
  CROP_RAID: "the_hunt",
  WINTER_RAIDERS: "desperate_times",
};

/**
 * Returns the public path to a card's full-art PNG based on its **stable base identity**, e.g.
 * "FARM" (at any level) → "/img/farm.png". Artwork is keyed off the base card, so "Farm IV" uses
 * the Farm art unless an explicit override exists. Falls back to the base-name convention for
 * unrecognised ids (legacy names are reduced to their base first).
 */
export function cardImagePath(cardName: string): string {
  const id = resolveIdentity(cardName, 1);
  const baseId = id ? id.base : cardName;
  const file = IMAGE_NAME_OVERRIDES[baseId] ?? baseId.toLowerCase();
  return `/img/${file}.png`;
}

/**
 * Attributes for a card `<img>`: the resolved source plus an `onerror` handler that swaps in the
 * missing-art placeholder if the PNG 404s. Keeps identity (name/description) and artwork loading
 * fully decoupled — a broken image can never surface as "Unknown Card".
 */
export function cardArtAttrs(cardName: string): string {
  return `src="${cardImagePath(cardName)}" onerror="this.onerror=null;this.src='${CARD_MISSING_ART}'"`;
}

/** Formats an effect modifier as a signed string, e.g. "+10" or "-25". */
export function formatModifier(modifier: number): string {
  return `${modifier >= 0 ? "+" : ""}${modifier}`;
}
