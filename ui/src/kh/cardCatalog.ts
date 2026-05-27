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
//
// Fingerprint format: {popReq}:{prodReq}:{FREQUENCY}:{TARGET}:{modifier}:{VALUE_TYPE}[|…]
// All values come directly from the Java constants in CardCatalog.java.

const DISPLAY_CATALOG: Record<string, CardDisplayInfo> = {
  // ── Spring ─────────────────────────────────────────────────────────────────
  // FARM          pop=5,   prod=30,  RECURRING,  FOOD+10
  "5:30:RECURRING:FOOD:10:INTEGER": {
    name: "Farm",
    icon: "🌿",
    description: "A working farm that reliably produces food each spring.",
    tags: ["AGRICULTURE"],
  },
  // SEEDS         pop=1,   prod=15,  SINGLE,     FOOD+10
  "1:15:SINGLE:FOOD:10:INTEGER": {
    name: "Seeds",
    icon: "🌱",
    description: "Scatter seeds across fallow land for a modest one-time harvest.",
    tags: ["AGRICULTURE"],
  },
  // FERTILIZER    pop=1,   prod=30,  RECURRING,  FOOD+10%
  "1:30:RECURRING:FOOD:10:PERCENTAGE": {
    name: "Fertilizer",
    icon: "🪣",
    description: "Enrich the soil each spring to boost all food output.",
    tags: ["AGRICULTURE"],
  },
  // MINE          pop=10,  prod=60,  RECURRING,  PRODUCTION+15
  "10:60:RECURRING:PRODUCTION:15:INTEGER": {
    name: "Mine",
    icon: "⛏️",
    description: "Dig into the hillside to extract ore every spring.",
    tags: ["INDUSTRY"],
  },
  // HUNTING_CAMP  pop=30,  prod=80,  RECURRING,  FOOD+12, PRODUCTION+8
  "30:80:RECURRING:FOOD:12:INTEGER|PRODUCTION:8:INTEGER": {
    name: "Hunting Camp",
    icon: "🏕️",
    description: "A seasonal hunting camp that brings in fresh game and tools each spring.",
    tags: ["FOOD", "INDUSTRY"],
  },
  // SETTLERS      pop=50,  prod=50,  SINGLE,     POPULATION+25
  "50:50:SINGLE:POPULATION:25:INTEGER": {
    name: "Settlers",
    icon: "🧳",
    description: "A wave of settlers arrives to grow your community.",
    tags: ["POPULATION"],
  },
  // LUMBER_YARD   pop=40,  prod=80,  RECURRING,  PRODUCTION+20, PRODUCTION+5%
  "40:80:RECURRING:PRODUCTION:20:INTEGER|PRODUCTION:5:PERCENTAGE": {
    name: "Lumber Yard",
    icon: "🪓",
    description: "A lumber yard that harvests timber and compounds your production each spring.",
    tags: ["INDUSTRY"],
  },
  // COMMUNITY_WELL  pop=100, prod=50,  RECURRING,  FOOD+10, POPULATION+10%
  "100:50:RECURRING:FOOD:10:INTEGER|POPULATION:10:PERCENTAGE": {
    name: "Community Well",
    icon: "⛲",
    description: "A shared well that keeps the village fed and steadily growing.",
    tags: ["AGRICULTURE", "POPULATION"],
  },
  // IRRIGATION    pop=100, prod=120, RECURRING,  FOOD+15%, FOOD+15
  "100:120:RECURRING:FOOD:15:PERCENTAGE|FOOD:15:INTEGER": {
    name: "Irrigation",
    icon: "💧",
    description: "Channel water to the fields, dramatically boosting crop yields each spring.",
    tags: ["AGRICULTURE"],
  },

  // ── Summer ─────────────────────────────────────────────────────────────────
  // FARMSTEAD     pop=20,  prod=50,  RECURRING,  FOOD+8, PRODUCTION+8
  "20:50:RECURRING:FOOD:8:INTEGER|PRODUCTION:8:INTEGER": {
    name: "Farmstead",
    icon: "🏡",
    description: "A prosperous farmstead that steadily produces both food and goods.",
    tags: ["AGRICULTURE", "INDUSTRY"],
  },
  // THE_HUNT      pop=15,  prod=20,  SINGLE,     FOOD+30
  "15:20:SINGLE:FOOD:30:INTEGER": {
    name: "The Hunt",
    icon: "🏹",
    description: "A summer hunt that brings in plentiful game for a one-time haul.",
    tags: ["FOOD"],
  },
  // LOGGING       pop=50,  prod=20,  SINGLE,     PRODUCTION+40
  "50:20:SINGLE:PRODUCTION:40:INTEGER": {
    name: "Logging",
    icon: "🪵",
    description: "A major logging operation that yields a large one-time surge in production.",
    tags: ["INDUSTRY"],
  },
  // TRADE_ROUTES  pop=80,  prod=80,  RECURRING,  PRODUCTION+5%, FOOD+5%
  "80:80:RECURRING:PRODUCTION:5:PERCENTAGE|FOOD:5:PERCENTAGE": {
    name: "Trade Routes",
    icon: "🚢",
    description: "Establish routes that grow both production and food every summer.",
    tags: ["COMMERCE"],
  },
  // FISHING_VILLAGE  pop=40,  prod=60,  RECURRING,  FOOD+15
  "40:60:RECURRING:FOOD:15:INTEGER": {
    name: "Fishing Village",
    icon: "🎣",
    description: "A coastal village that fishes the summer waters for steady food.",
    tags: ["FOOD"],
  },
  // SCHOOL_HOUSE  pop=100, prod=75,  RECURRING,  PRODUCTION+8%, FOOD+5%
  "100:75:RECURRING:PRODUCTION:8:PERCENTAGE|FOOD:5:PERCENTAGE": {
    name: "School House",
    icon: "🏫",
    description: "A school that trains workers and improves your kingdom's output.",
    tags: ["INDUSTRY", "AGRICULTURE"],
  },
  // EARLY_HARVEST pop=10,  prod=30,  SINGLE,     FOOD+35
  "10:30:SINGLE:FOOD:35:INTEGER": {
    name: "Early Harvest",
    icon: "🌽",
    description: "Gather the first crops of summer before the heat peaks.",
    tags: ["AGRICULTURE"],
  },
  // WINDMILL      pop=2,   prod=70,  RECURRING,  FOOD+10, FOOD+5%, PRODUCTION+5
  "2:70:RECURRING:FOOD:10:INTEGER|FOOD:5:PERCENTAGE|PRODUCTION:5:INTEGER": {
    name: "Windmill",
    icon: "🌬️",
    description: "A windmill that grinds grain and keeps production turning each summer.",
    tags: ["AGRICULTURE", "INDUSTRY"],
  },
  // MARKETPLACE   pop=80,  prod=70,  RECURRING,  PRODUCTION+10%, FOOD+10
  "80:70:RECURRING:PRODUCTION:10:PERCENTAGE|FOOD:10:INTEGER": {
    name: "Marketplace",
    icon: "🏪",
    description: "A bustling marketplace that drives commerce and brings in provisions.",
    tags: ["COMMERCE"],
  },
  // CARAVAN       pop=80,  prod=70,  RECURRING,  PRODUCTION+10, FOOD+10%
  "80:70:RECURRING:PRODUCTION:10:INTEGER|FOOD:10:PERCENTAGE": {
    name: "Caravan",
    icon: "🐪",
    description: "A trade caravan that returns each summer with goods and extra provisions.",
    tags: ["COMMERCE"],
  },
  // TOWN_FAIR     pop=30,  prod=40,  SINGLE,     POPULATION+10, FOOD+20
  "30:40:SINGLE:POPULATION:10:INTEGER|FOOD:20:INTEGER": {
    name: "Town Fair",
    icon: "🎠",
    description: "A summer fair that draws in new settlers and fills the larder.",
    tags: ["POPULATION", "FOOD"],
  },
  // STONE_QUARRY  pop=10,  prod=60,  RECURRING,  PRODUCTION+15, PRODUCTION+5%
  "10:60:RECURRING:PRODUCTION:15:INTEGER|PRODUCTION:5:PERCENTAGE": {
    name: "Stone Quarry",
    icon: "🪨",
    description: "A quarry that extracts stone each summer, boosting and compounding production.",
    tags: ["INDUSTRY"],
  },
  // MERCHANT_GUILD  pop=150, prod=120, RECURRING,  PRODUCTION+10%, FOOD+10%
  "150:120:RECURRING:PRODUCTION:10:PERCENTAGE|FOOD:10:PERCENTAGE": {
    name: "Merchant Guild",
    icon: "💰",
    description: "An established guild that expands both production and food stores.",
    tags: ["COMMERCE"],
  },

  // ── Fall ───────────────────────────────────────────────────────────────────
  // WORKSHOP      pop=10,  prod=40,  RECURRING,  PRODUCTION+10
  "10:40:RECURRING:PRODUCTION:10:INTEGER": {
    name: "Workshop",
    icon: "🔧",
    description: "A workshop that steadily expands your production capacity each fall.",
    tags: ["INDUSTRY"],
  },
  // MANOR         pop=80,  prod=75,  RECURRING,  POPULATION+5, PRODUCTION+20
  "80:75:RECURRING:POPULATION:5:INTEGER|PRODUCTION:20:INTEGER": {
    name: "Manor",
    icon: "🏰",
    description: "A grand manor that attracts settlers and skilled workers each fall.",
    tags: ["POPULATION", "INDUSTRY"],
  },
  // HARVEST       pop=5,   prod=20,  SINGLE,     FOOD+40
  "5:20:SINGLE:FOOD:40:INTEGER": {
    name: "Harvest",
    icon: "🌾",
    description: "A bountiful fall harvest that fills your stores.",
    tags: ["AGRICULTURE"],
  },
  // TEXTILES      pop=25,  prod=60,  RECURRING,  PRODUCTION+5%, PRODUCTION+10
  "25:60:RECURRING:PRODUCTION:5:PERCENTAGE|PRODUCTION:10:INTEGER": {
    name: "Textiles",
    icon: "🧵",
    description: "A textile mill that compounds and boosts your production output.",
    tags: ["INDUSTRY"],
  },
  // BAKERY        pop=30,  prod=60,  RECURRING,  FOOD+12, FOOD+5%
  "30:60:RECURRING:FOOD:12:INTEGER|FOOD:5:PERCENTAGE": {
    name: "Bakery",
    icon: "🍞",
    description: "A bakery that turns the harvest grain into steady and growing food output.",
    tags: ["FOOD"],
  },
  // TAX_COLLECTION  pop=100, prod=0,   SINGLE,     PRODUCTION+40, POPULATION-10
  "100:0:SINGLE:PRODUCTION:40:INTEGER|POPULATION:-10:INTEGER": {
    name: "Tax Collection",
    icon: "📜",
    description: "Levy taxes for a one-time surge in production — but the people pay the price.",
    tags: ["COMMERCE"],
  },
  // STOREHOUSE    pop=40,  prod=45,  RECURRING,  FOOD+8%
  "40:45:RECURRING:FOOD:8:PERCENTAGE": {
    name: "Storehouse",
    icon: "📦",
    description: "A storehouse that stretches your food reserves each fall.",
    tags: ["AGRICULTURE"],
  },
  // BARN          pop=70,  prod=40,  RECURRING,  FOOD+20
  "70:40:RECURRING:FOOD:20:INTEGER": {
    name: "Barn",
    icon: "🐄",
    description: "A barn that shelters livestock and keeps food production steady.",
    tags: ["AGRICULTURE"],
  },
  // MASON_GUILD   pop=100, prod=100, RECURRING,  PRODUCTION+25, PRODUCTION+10%
  "100:100:RECURRING:PRODUCTION:25:INTEGER|PRODUCTION:10:PERCENTAGE": {
    name: "Mason Guild",
    icon: "🏗️",
    description: "A guild of master masons who drive substantial and compounding production.",
    tags: ["INDUSTRY"],
  },
  // STONE_TOOLS   pop=10,  prod=40,  RECURRING,  PRODUCTION+5%
  "10:40:RECURRING:PRODUCTION:5:PERCENTAGE": {
    name: "Stone Tools",
    icon: "🪨",
    description: "Craft stone tools each fall to steadily improve your kingdom's output.",
    tags: ["INDUSTRY"],
  },
  // METALWORKING  pop=50,  prod=80,  RECURRING,  PRODUCTION+10%
  "50:80:RECURRING:PRODUCTION:10:PERCENTAGE": {
    name: "Metalworking",
    icon: "⚙️",
    description: "A metalworking operation that compounds your production output each fall.",
    tags: ["INDUSTRY"],
  },
  // BLACKSMITH    pop=150, prod=160, RECURRING,  PRODUCTION+20%
  "150:160:RECURRING:PRODUCTION:20:PERCENTAGE": {
    name: "Blacksmith",
    icon: "🔨",
    description: "A master blacksmith's forge that dramatically accelerates production each fall.",
    tags: ["INDUSTRY"],
  },

  // ── Winter ─────────────────────────────────────────────────────────────────
  // FORGE         pop=20,  prod=50,  RECURRING,  PRODUCTION+15
  "20:50:RECURRING:PRODUCTION:15:INTEGER": {
    name: "Forge",
    icon: "🔥",
    description: "A forge that keeps production running through the long winter.",
    tags: ["INDUSTRY"],
  },
  // WOODPILES     pop=10,  prod=10,  SINGLE,     PRODUCTION+25
  "10:10:SINGLE:PRODUCTION:25:INTEGER": {
    name: "Woodpiles",
    icon: "🪵",
    description: "Stock up on firewood to endure the cold months.",
    tags: ["SURVIVAL"],
  },
  // GRANARY       pop=50,  prod=100, RECURRING,  FOOD+10%, FOOD+15
  "50:100:RECURRING:FOOD:10:PERCENTAGE|FOOD:15:INTEGER": {
    name: "Granary",
    icon: "🌾",
    description: "A granary that stretches and supplements your food supplies through winter.",
    tags: ["FOOD"],
  },
  // EMERGENCY_RATIONS  pop=10,  prod=25,  SINGLE,     FOOD+25
  "10:25:SINGLE:FOOD:25:INTEGER": {
    name: "Emergency Rations",
    icon: "🥫",
    description: "Break open emergency stores to survive the harshest winter nights.",
    tags: ["SURVIVAL", "FOOD"],
  },
  // STOCKPILE     pop=30,  prod=65,  SINGLE,     PRODUCTION+15, FOOD+5%
  "30:65:SINGLE:PRODUCTION:15:INTEGER|FOOD:5:PERCENTAGE": {
    name: "Stockpile",
    icon: "🗄️",
    description: "Draw on stockpiled reserves for a one-time boost to production and food.",
    tags: ["SURVIVAL"],
  },
  // CHAPEL        pop=35,  prod=40,  RECURRING,  FOOD+40, POPULATION+20%
  "35:40:RECURRING:FOOD:40:INTEGER|POPULATION:20:PERCENTAGE": {
    name: "Chapel",
    icon: "⛪",
    description: "A chapel that fosters hope through winter, filling bellies and drawing people in.",
    tags: ["FOOD", "POPULATION"],
  },
  // PRAY          pop=1,   prod=1,   SINGLE,     FOOD+5, PRODUCTION+5
  "1:1:SINGLE:FOOD:5:INTEGER|PRODUCTION:5:INTEGER": {
    name: "Pray",
    icon: "🙏",
    description: "Ask for divine guidance. A small blessing of food and production when all else fails.",
    tags: ["SURVIVAL"],
  },
  // DESPERATE_TIMES  pop=10,  prod=10,  RECURRING,  FOOD+40, POPULATION-10
  "10:10:RECURRING:FOOD:40:INTEGER|POPULATION:-10:INTEGER": {
    name: "Desperate Times",
    icon: "⚠️",
    description: "Extreme measures fill the larder — but the cost to your people is steep.",
    tags: ["SURVIVAL"],
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
