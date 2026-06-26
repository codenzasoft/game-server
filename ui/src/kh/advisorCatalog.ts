// ─── Advisor display metadata ──────────────────────────────────────────────────
//
// Mirrors AdvisorCatalog.java's 15 static advisor definitions on the client,
// the same way cardCatalog.ts mirrors CardCatalog.java. The server already
// sends full Advisor objects (id, displayName, title, specialtyDescription,
// hiringCost, ringColor, portraitPlaceholderId, …) inside councilOfferChoices
// during COUNCIL_ADD, so this catalog isn't needed there. It exists for the
// COUNCIL_MEETING phase and the seated-council roster, where the wire only
// sends a SeatedAdvisor (advisorId + accrued bonuses) — this file fills in the
// name/title/portrait/Reform/Decree details needed to render them.
//
// Keys match the `id` field on the Java Advisor record (AdvisorCatalog.java).
// Add a new entry here whenever a new advisor constant is added on the server.

import { ReformBenefitType, DecreeType, ResourceTarget, SeatedAdvisor } from "./kingshand.ts";

export interface AdvisorReformInfo {
  name: string;
  maxStacks: number;
  benefitType: ReformBenefitType;
  benefitResource?: ResourceTarget;
  benefitPerStack: number;
  costResource: ResourceTarget;
  costPerStack: number;
}

export interface AdvisorDecreeInfo {
  name: string;
  type: DecreeType;
  targetGreatWorkName?: string;
  effectResource?: ResourceTarget;
  effectAmount: number;
}

export interface AdvisorDisplayInfo {
  displayName: string;
  title: string;
  specialtyDescription: string;
  /** One-time gold cost to hire this advisor — mirrors {@code Advisor.hiringCost} on the server.
   *  Needed client-side to price the "let go" dismissal cost for seated advisors, since the wire
   *  only sends a lean SeatedAdvisor (no hiringCost) once an advisor is seated. */
  hiringCost: number;
  /** Drives the placeholder-portrait ring color — see {@link ringColorHex}. */
  ringColor: string;
  portraitPlaceholderId: string;
  /** Base count of additional free ADD-phase rerolls while seated — mirrors
   *  {@code Advisor.freeRerollsWhileSeated} on the server. 0 (omitted) for every advisor except
   *  Joric Wren. See {@link seatedFreeRerollBonus}, which sums this across the whole council
   *  to compute the ADD-phase reroll button's true cost. */
  freeRerollsWhileSeated?: number;
  reform: AdvisorReformInfo;
  decree: AdvisorDecreeInfo;
}

const UNKNOWN_ADVISOR: AdvisorDisplayInfo = {
  displayName: "Unknown Advisor",
  title: "Unknown",
  specialtyDescription: "",
  hiringCost: 0,
  ringColor: "slate-grey",
  portraitPlaceholderId: "unknown_placeholder",
  reform: {
    name: "Unknown Reform",
    maxStacks: 0,
    benefitType: ReformBenefitType.CARD_EFFECT,
    benefitPerStack: 0,
    costResource: ResourceTarget.GOLD,
    costPerStack: 0,
  },
  decree: {
    name: "Unknown Decree",
    type: DecreeType.PASSIVE_BOOST,
    effectAmount: 0,
  },
};

const ADVISOR_DISPLAY_CATALOG: Record<string, AdvisorDisplayInfo> = {
  REEVE_ALDWIN: {
    displayName: "Reeve Aldwin",
    title: "The Overseer",
    specialtyDescription:
      "Competent and unglamorous, Aldwin keeps the granaries running and the ledgers balanced without ever needing to be asked twice.",
    hiringCost: 180,
    ringColor: "slate-grey",
    portraitPlaceholderId: "reeve_aldwin_placeholder",
    reform: {
      name: "Audit the Granaries",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.PRODUCTION,
      benefitPerStack: 90,
      costResource: ResourceTarget.POPULATION,
      costPerStack: -45,
    },
    decree: {
      name: "Found the Town Hall",
      type: DecreeType.FREE_REROLL,
      effectAmount: 1,
    },
  },
  MIRA_VOSS: {
    displayName: "Mira Voss",
    title: "The Treasurer",
    specialtyDescription:
      "Sharp with numbers and sharper in a negotiation, Voss keeps the coffers fuller than anyone expects.",
    hiringCost: 360,
    ringColor: "amber",
    portraitPlaceholderId: "mira_voss_placeholder",
    reform: {
      name: "Raise the Levy",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.GOLD,
      benefitPerStack: 38,
      costResource: ResourceTarget.POPULATION,
      costPerStack: -38,
    },
    decree: {
      name: "Mint a Currency",
      type: DecreeType.GREAT_WORK_BOOST,
      targetGreatWorkName: "TREASURY",
      effectResource: ResourceTarget.GOLD,
      effectAmount: 23,
    },
  },
  SIR_BRAM_HOLT: {
    displayName: "Sir Bram Holt",
    title: "The Lord Marshal",
    specialtyDescription:
      "A grizzled veteran who drills the garrison hard and trusts results over rank.",
    hiringCost: 300,
    ringColor: "deep-red",
    portraitPlaceholderId: "sir_bram_holt_placeholder",
    reform: {
      name: "Conscript Levies",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.STRENGTH,
      benefitPerStack: 23,
      costResource: ResourceTarget.POPULATION,
      costPerStack: -15,
    },
    decree: {
      name: "Drill the Garrison",
      type: DecreeType.GREAT_WORK_BOOST,
      targetGreatWorkName: "BARRACKS",
      effectResource: ResourceTarget.STRENGTH,
      effectAmount: 30,
    },
  },
  OLD_YARROW: {
    displayName: "Old Yarrow",
    title: "The Court Physician",
    specialtyDescription:
      "A blunt, no-nonsense healer who's seen every fever and famine the realm can offer, and still keeps the larders full.",
    hiringCost: 105,
    ringColor: "green",
    portraitPlaceholderId: "old_yarrow_placeholder",
    reform: {
      name: "Stock the Apothecary",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.FOOD,
      benefitPerStack: 38,
      costResource: ResourceTarget.PRODUCTION,
      costPerStack: -23,
    },
    decree: {
      name: "Found the Almshouse",
      type: DecreeType.POPULATION_CAP_BOOST,
      effectAmount: 23,
    },
  },
  TOMAS_FERRO: {
    displayName: "Tomas Ferro",
    title: "The Master Engineer",
    specialtyDescription:
      "A tireless tinkerer drawn to the scaffolding of any grand project, always certain the next design will be sturdier than the last.",
    hiringCost: 165,
    ringColor: "slate-grey",
    portraitPlaceholderId: "tomas_ferro_placeholder",
    reform: {
      name: "Stockpile Tools",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.PRODUCTION,
      benefitPerStack: 30,
      costResource: ResourceTarget.GOLD,
      costPerStack: -15,
    },
    decree: {
      name: "Expedite Construction",
      type: DecreeType.GREAT_WORK_ACCELERATE,
      effectAmount: 1,
    },
  },
  LADY_ISEULT_VARN: {
    displayName: "Lady Iseult Varn",
    title: "The Broker",
    specialtyDescription:
      "Keeps a network of informants that always turns up one more name worth considering when the court convenes.",
    hiringCost: 330,
    ringColor: "violet",
    portraitPlaceholderId: "lady_iseult_varn_placeholder",
    reform: {
      name: "Pay Her Network",
      maxStacks: 1,
      benefitType: ReformBenefitType.EXTRA_ADD_CANDIDATE,
      benefitPerStack: 1,
      costResource: ResourceTarget.GOLD,
      costPerStack: -23,
    },
    decree: {
      name: "Plant an Informant",
      type: DecreeType.FREE_REROLL,
      effectAmount: 1,
    },
  },
  BROTHER_CASSIAN: {
    displayName: "Brother Cassian",
    title: "The High Chaplain",
    specialtyDescription:
      "A gentle, steady presence who tends to the sick and newly born alike, and always seems to know just what to say.",
    hiringCost: 135,
    ringColor: "dusty-blue",
    portraitPlaceholderId: "brother_cassian_placeholder",
    reform: {
      name: "Hold a Sermon of Thanks",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.POPULATION,
      benefitPerStack: 6,
      costResource: ResourceTarget.FOOD,
      costPerStack: -60,
    },
    decree: {
      name: "Consecrate the Reservoir",
      type: DecreeType.GREAT_WORK_BOOST,
      targetGreatWorkName: "THE_GREAT_RESERVOIR",
      effectResource: ResourceTarget.FOOD,
      effectAmount: 300,
    },
  },
  GARRICK_STONEBACK: {
    displayName: "Garrick Stoneback",
    title: "The Quarrymaster",
    specialtyDescription:
      "Built like the stone he cuts, Stoneback has never met a vein of rock he couldn't crack open.",
    hiringCost: 98,
    ringColor: "slate-grey",
    portraitPlaceholderId: "garrick_stoneback_placeholder",
    reform: {
      name: "Open a New Vein",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.PRODUCTION,
      benefitPerStack: 45,
      costResource: ResourceTarget.FOOD,
      costPerStack: -30,
    },
    decree: {
      name: "Open the Royal Quarry",
      type: DecreeType.PASSIVE_BOOST,
      effectResource: ResourceTarget.PRODUCTION,
      effectAmount: 53,
    },
  },
  DAME_PETRA_LYSK: {
    displayName: "Dame Petra Lysk",
    title: "The Harbor Mistress",
    specialtyDescription:
      "A shrewd negotiator who knows every merchant captain in port by name, and exactly how much they owe her.",
    hiringCost: 195,
    ringColor: "amber",
    portraitPlaceholderId: "dame_petra_lysk_placeholder",
    reform: {
      name: "Chart New Routes",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.GOLD,
      benefitPerStack: 12,
      costResource: ResourceTarget.PRODUCTION,
      costPerStack: -23,
    },
    decree: {
      name: "Charter the Trade Road",
      type: DecreeType.GREAT_WORK_BOOST,
      targetGreatWorkName: "THE_GREAT_ROAD",
      effectResource: ResourceTarget.GOLD,
      effectAmount: 450,
    },
  },
  WENDEL_ASHGROVE: {
    displayName: "Wendel Ashgrove",
    title: "The Royal Forester",
    specialtyDescription:
      "Knows every deer trail and berry patch in the royal wood, and never comes back empty-handed.",
    hiringCost: 90,
    ringColor: "green",
    portraitPlaceholderId: "wendel_ashgrove_placeholder",
    reform: {
      name: "Clear New Fields",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.FOOD,
      benefitPerStack: 38,
      costResource: ResourceTarget.PRODUCTION,
      costPerStack: -30,
    },
    decree: {
      name: "Settle the Old Groves",
      type: DecreeType.POPULATION_CAP_BOOST,
      effectAmount: 60,
    },
  },
  MAESTER_JORIC: {
    displayName: "Joric Wren",
    title: "The Royal Archivist",
    specialtyDescription:
      "A meticulous record-keeper who's always already double-checked your decision before you've finished making it, and isn't shy about offering a free second opinion.",
    hiringCost: 210,
    ringColor: "violet",
    portraitPlaceholderId: "maester_joric_placeholder",
    freeRerollsWhileSeated: 1,
    reform: {
      name: "Expand the Archive",
      maxStacks: 1,
      benefitType: ReformBenefitType.FREE_REROLL,
      benefitPerStack: 1,
      costResource: ResourceTarget.GOLD,
      costPerStack: -30,
    },
    decree: {
      name: "Catalogue the Treasury",
      type: DecreeType.GREAT_WORK_BOOST,
      targetGreatWorkName: "TREASURY",
      effectResource: ResourceTarget.PRODUCTION,
      effectAmount: 75,
    },
  },
  BRYNN_OSTLAR: {
    displayName: "Brynn Ostlar",
    title: "The Stablemaster",
    specialtyDescription:
      "Has an eye for good bloodlines and broken wagon wheels alike — whatever in the yard needs the most help, she's already there.",
    hiringCost: 315,
    ringColor: "slate-grey",
    portraitPlaceholderId: "brynn_ostlar_placeholder",
    reform: {
      name: "Breed Finer Stock",
      maxStacks: 3,
      benefitType: ReformBenefitType.CROSS_TABLE_BOOST,
      benefitPerStack: 8,
      costResource: ResourceTarget.FOOD,
      costPerStack: -30,
    },
    decree: {
      name: "Build the Stables",
      type: DecreeType.PASSIVE_BOOST,
      effectResource: ResourceTarget.PRODUCTION,
      effectAmount: 60,
    },
  },
  HEWETT_CRAY: {
    displayName: "Hewett Cray",
    title: "The Foreman",
    specialtyDescription:
      "Runs every worksite like it's behind schedule, because in his mind, it always is.",
    hiringCost: 150,
    ringColor: "slate-grey",
    portraitPlaceholderId: "hewett_cray_placeholder",
    reform: {
      name: "Commission a Survey",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.PRODUCTION,
      benefitPerStack: 30,
      costResource: ResourceTarget.GOLD,
      costPerStack: -18,
    },
    decree: {
      name: "Raise New Wards",
      type: DecreeType.POPULATION_CAP_BOOST,
      effectAmount: 113,
    },
  },
  CAPTAIN_YSEULT_MARROW: {
    displayName: "Captain Yseult Marrow",
    title: "Captain of the Guard",
    specialtyDescription:
      "Keeps the guard sharp and the work crews moving, often by little more than the threat of her glare.",
    hiringCost: 128,
    ringColor: "slate-grey",
    portraitPlaceholderId: "captain_yseult_marrow_placeholder",
    reform: {
      name: "Drill the Guard",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.PRODUCTION,
      benefitPerStack: 23,
      costResource: ResourceTarget.POPULATION,
      costPerStack: -9,
    },
    decree: {
      name: "Walk the Walls",
      type: DecreeType.PASSIVE_BOOST,
      effectResource: ResourceTarget.PRODUCTION,
      effectAmount: 45,
    },
  },
  MOTHER_BRASK: {
    displayName: "Mother Brask",
    title: "Keeper of the Hearth",
    specialtyDescription:
      "Runs the household like a small kingdom of her own — fed, warm, and never short of advice nobody asked for.",
    hiringCost: 113,
    ringColor: "dusty-blue",
    portraitPlaceholderId: "mother_brask_placeholder",
    reform: {
      name: "Open the Birthing House",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.POPULATION,
      benefitPerStack: 8,
      costResource: ResourceTarget.FOOD,
      costPerStack: -68,
    },
    decree: {
      name: "Bless the Hearths",
      type: DecreeType.POPULATION_CAP_BOOST,
      effectAmount: 75,
    },
  },
};

/** Looks up an advisor's display info by stable catalog id. Falls back to a generic placeholder. */
export function getAdvisorDisplayInfo(advisorId: string): AdvisorDisplayInfo {
  return ADVISOR_DISPLAY_CATALOG[advisorId] ?? UNKNOWN_ADVISOR;
}

/**
 * Total extra free ADD-phase rerolls granted by every seated advisor's base trait plus any
 * Reform-driven bonus accrued on that seat — mirrors {@code GameEngine.seatedFreeRerollBonus()}
 * exactly. Combine with {@code GameState.permanentFreeRerolls} (Decree-driven, kingdom-wide) to
 * reproduce the server's {@code freeCalls} total for the ADD-phase reroll button's true cost.
 */
export function seatedFreeRerollBonus(council: SeatedAdvisor[]): number {
  return council.reduce((total, seated) => {
    const info = getAdvisorDisplayInfo(seated.advisorId);
    return total + (info.freeRerollsWhileSeated ?? 0) + seated.bonusFreeRerolls;
  }, 0);
}

// ─── Placeholder portrait rendering ────────────────────────────────────────────
//
// Real per-advisor art has not been produced yet. Per AdvisorCatalog.java's
// javadoc, the agreed placeholder is "a colored ring around a generic profile
// circle" — ringColor picks the ring, portraitPlaceholderId is reserved for
// swapping in real art later (e.g. mapping it to an <img> src) without
// touching any rendering call sites.

const RING_COLOR_HEX: Record<string, string> = {
  "slate-grey": "#6b7785",
  amber: "#caa23a",
  "deep-red": "#8b2020",
  green: "#3d6b46",
  violet: "#7a4fa0",
  "dusty-blue": "#4a6a8a",
};

/** Resolves a catalog ring-color keyword (e.g. "slate-grey") to a hex color. */
export function ringColorHex(ringColor: string): string {
  return RING_COLOR_HEX[ringColor] ?? "#6b7785";
}

/**
 * Builds the placeholder portrait HTML: a colored ring around a generic profile
 * silhouette. Swap this out for an `<img>` of real art once available — every
 * call site only needs `ringColor`, so the swap is contained to this function.
 */
export function buildPortraitPlaceholder(ringColor: string, sizePx = 72): string {
  const hex = ringColorHex(ringColor);
  return `
    <div class="kh-advisor-portrait" style="
      width:${sizePx}px;
      height:${sizePx}px;
      min-width:${sizePx}px;
      border-radius:50%;
      border:3px solid ${hex};
      box-shadow:0 0 0 2px rgba(0,0,0,.4), 0 3px 10px rgba(0,0,0,.45), inset 0 0 12px rgba(0,0,0,.5);
      background:radial-gradient(circle at 35% 30%, #453a30 0%, #1c1712 75%);
      display:flex;
      align-items:center;
      justify-content:center;
      flex-shrink:0;
    ">
      <span style="font-size:${Math.round(sizePx * 0.5)}px;line-height:1;opacity:.6;filter:grayscale(1) brightness(1.7);">👤</span>
    </div>
  `;
}
