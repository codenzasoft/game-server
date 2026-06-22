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

import { ReformBenefitType, DecreeType, ResourceTarget } from "./kingshand.ts";

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
  /** Drives the placeholder-portrait ring color — see {@link ringColorHex}. */
  ringColor: string;
  portraitPlaceholderId: string;
  reform: AdvisorReformInfo;
  decree: AdvisorDecreeInfo;
}

const UNKNOWN_ADVISOR: AdvisorDisplayInfo = {
  displayName: "Unknown Advisor",
  title: "Unknown",
  specialtyDescription: "",
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
    title: "The Steward",
    specialtyDescription:
      "+60 Production/season. The default first hire — competent, unglamorous, always useful.",
    ringColor: "slate-grey",
    portraitPlaceholderId: "reeve_aldwin_placeholder",
    reform: {
      name: "Audit the Granaries",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.PRODUCTION,
      benefitPerStack: 20,
      costResource: ResourceTarget.POPULATION,
      costPerStack: -5,
    },
    decree: {
      name: "Found the Town Hall",
      type: DecreeType.FREE_REROLL,
      effectAmount: 1,
    },
  },
  MIRA_VOSS: {
    displayName: "Mira Voss",
    title: "The Master of Coin",
    specialtyDescription: "+15 Gold/season.",
    ringColor: "amber",
    portraitPlaceholderId: "mira_voss_placeholder",
    reform: {
      name: "Raise the Levy",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.GOLD,
      benefitPerStack: 10,
      costResource: ResourceTarget.POPULATION,
      costPerStack: -8,
    },
    decree: {
      name: "Mint a Currency",
      type: DecreeType.GREAT_WORK_BOOST,
      targetGreatWorkName: "TREASURY",
      effectResource: ResourceTarget.GOLD,
      effectAmount: 15,
    },
  },
  SIR_BRAM_HOLT: {
    displayName: "Sir Bram Holt",
    title: "The Lord Marshal",
    specialtyDescription:
      "+25 Strength/season. Only appears in the offer pool once BARRACKS is completed.",
    ringColor: "deep-red",
    portraitPlaceholderId: "sir_bram_holt_placeholder",
    reform: {
      name: "Conscript Levies",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.STRENGTH,
      benefitPerStack: 15,
      costResource: ResourceTarget.POPULATION,
      costPerStack: -10,
    },
    decree: {
      name: "Drill the Garrison",
      type: DecreeType.GREAT_WORK_BOOST,
      targetGreatWorkName: "BARRACKS",
      effectResource: ResourceTarget.STRENGTH,
      effectAmount: 20,
    },
  },
  OLD_YARROW: {
    displayName: "Old Yarrow",
    title: "The Court Physician",
    specialtyDescription: "+80 Food/season.",
    ringColor: "green",
    portraitPlaceholderId: "old_yarrow_placeholder",
    reform: {
      name: "Stock the Apothecary",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.FOOD,
      benefitPerStack: 25,
      costResource: ResourceTarget.PRODUCTION,
      costPerStack: -15,
    },
    decree: {
      name: "Found the Almshouse",
      type: DecreeType.POPULATION_CAP_BOOST,
      effectAmount: 15,
    },
  },
  TOMAS_FERRO: {
    displayName: "Tomas Ferro",
    title: "The Master Engineer",
    specialtyDescription:
      "+50 Production/season. Only appears once at least one Great Work has been commissioned.",
    ringColor: "slate-grey",
    portraitPlaceholderId: "tomas_ferro_placeholder",
    reform: {
      name: "Stockpile Tools",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.PRODUCTION,
      benefitPerStack: 20,
      costResource: ResourceTarget.GOLD,
      costPerStack: -10,
    },
    decree: {
      name: "Expedite Construction",
      type: DecreeType.GREAT_WORK_ACCELERATE,
      effectAmount: 1,
    },
  },
  LADY_ISEULT_VARN: {
    displayName: "Lady Iseult Varn",
    title: "The Spymaster",
    specialtyDescription: "Reveals one extra candidate in every ADD-phase offer while seated.",
    ringColor: "violet",
    portraitPlaceholderId: "lady_iseult_varn_placeholder",
    reform: {
      name: "Pay Her Network",
      maxStacks: 1,
      benefitType: ReformBenefitType.EXTRA_ADD_CANDIDATE,
      benefitPerStack: 1,
      costResource: ResourceTarget.GOLD,
      costPerStack: -15,
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
    specialtyDescription: "+6 Population/season.",
    ringColor: "dusty-blue",
    portraitPlaceholderId: "brother_cassian_placeholder",
    reform: {
      name: "Hold a Sermon of Thanks",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.POPULATION,
      benefitPerStack: 4,
      costResource: ResourceTarget.FOOD,
      costPerStack: -40,
    },
    decree: {
      name: "Consecrate the Reservoir",
      type: DecreeType.GREAT_WORK_BOOST,
      targetGreatWorkName: "THE_GREAT_RESERVOIR",
      effectResource: ResourceTarget.FOOD,
      effectAmount: 200,
    },
  },
  GARRICK_STONEBACK: {
    displayName: "Garrick Stoneback",
    title: "The Quarrymaster",
    specialtyDescription: "+90 Production/season.",
    ringColor: "slate-grey",
    portraitPlaceholderId: "garrick_stoneback_placeholder",
    reform: {
      name: "Open a New Vein",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.PRODUCTION,
      benefitPerStack: 30,
      costResource: ResourceTarget.FOOD,
      costPerStack: -20,
    },
    decree: {
      name: "Open the Royal Quarry",
      type: DecreeType.PASSIVE_BOOST,
      effectResource: ResourceTarget.PRODUCTION,
      effectAmount: 35,
    },
  },
  DAME_PETRA_LYSK: {
    displayName: "Dame Petra Lysk",
    title: "The Harbor Mistress",
    specialtyDescription: "+12 Gold/season. Only appears once THE_GREAT_ROAD has been commissioned.",
    ringColor: "amber",
    portraitPlaceholderId: "dame_petra_lysk_placeholder",
    reform: {
      name: "Chart New Routes",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.GOLD,
      benefitPerStack: 8,
      costResource: ResourceTarget.PRODUCTION,
      costPerStack: -15,
    },
    decree: {
      name: "Charter the Trade Road",
      type: DecreeType.GREAT_WORK_BOOST,
      targetGreatWorkName: "THE_GREAT_ROAD",
      effectResource: ResourceTarget.GOLD,
      effectAmount: 300,
    },
  },
  WENDEL_ASHGROVE: {
    displayName: "Wendel Ashgrove",
    title: "The Royal Forester",
    specialtyDescription: "+70 Food/season.",
    ringColor: "green",
    portraitPlaceholderId: "wendel_ashgrove_placeholder",
    reform: {
      name: "Clear New Fields",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.FOOD,
      benefitPerStack: 25,
      costResource: ResourceTarget.PRODUCTION,
      costPerStack: -20,
    },
    decree: {
      name: "Settle the Old Groves",
      type: DecreeType.POPULATION_CAP_BOOST,
      effectAmount: 40,
    },
  },
  MAESTER_JORIC: {
    displayName: "Maester Joric",
    title: "The Royal Archivist",
    specialtyDescription: "The first reroll each season is free while he's seated.",
    ringColor: "violet",
    portraitPlaceholderId: "maester_joric_placeholder",
    reform: {
      name: "Expand the Archive",
      maxStacks: 1,
      benefitType: ReformBenefitType.FREE_REROLL,
      benefitPerStack: 1,
      costResource: ResourceTarget.GOLD,
      costPerStack: -20,
    },
    decree: {
      name: "Catalogue the Treasury",
      type: DecreeType.GREAT_WORK_BOOST,
      targetGreatWorkName: "TREASURY",
      effectResource: ResourceTarget.PRODUCTION,
      effectAmount: 50,
    },
  },
  BRYNN_OSTLAR: {
    displayName: "Brynn Ostlar",
    title: "The Master of Horse",
    specialtyDescription:
      "Boosts the first Production-producing card on the active season's table by +10 each season.",
    ringColor: "slate-grey",
    portraitPlaceholderId: "brynn_ostlar_placeholder",
    reform: {
      name: "Breed Finer Stock",
      maxStacks: 3,
      benefitType: ReformBenefitType.CROSS_TABLE_BOOST,
      benefitPerStack: 5,
      costResource: ResourceTarget.FOOD,
      costPerStack: -20,
    },
    decree: {
      name: "Build the Stables",
      type: DecreeType.PASSIVE_BOOST,
      effectResource: ResourceTarget.PRODUCTION,
      effectAmount: 40,
    },
  },
  HEWETT_CRAY: {
    displayName: "Hewett Cray",
    title: "The Master of Works",
    specialtyDescription: "+55 Production/season.",
    ringColor: "slate-grey",
    portraitPlaceholderId: "hewett_cray_placeholder",
    reform: {
      name: "Commission a Survey",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.PRODUCTION,
      benefitPerStack: 20,
      costResource: ResourceTarget.GOLD,
      costPerStack: -12,
    },
    decree: {
      name: "Raise New Wards",
      type: DecreeType.POPULATION_CAP_BOOST,
      effectAmount: 75,
    },
  },
  CAPTAIN_YSEULT_MARROW: {
    displayName: "Captain Yseult Marrow",
    title: "Captain of the Watch",
    specialtyDescription:
      "+50 Production/season. Available from the start of a run — the early-game counterpart to Sir Bram Holt.",
    ringColor: "slate-grey",
    portraitPlaceholderId: "captain_yseult_marrow_placeholder",
    reform: {
      name: "Drill the Watch",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.PRODUCTION,
      benefitPerStack: 15,
      costResource: ResourceTarget.POPULATION,
      costPerStack: -6,
    },
    decree: {
      name: "Walk the Walls",
      type: DecreeType.PASSIVE_BOOST,
      effectResource: ResourceTarget.PRODUCTION,
      effectAmount: 30,
    },
  },
  MOTHER_BRASK: {
    displayName: "Mother Brask",
    title: "Keeper of the Hearth",
    specialtyDescription: "+8 Population/season.",
    ringColor: "dusty-blue",
    portraitPlaceholderId: "mother_brask_placeholder",
    reform: {
      name: "Open the Birthing House",
      maxStacks: 3,
      benefitType: ReformBenefitType.CARD_EFFECT,
      benefitResource: ResourceTarget.POPULATION,
      benefitPerStack: 5,
      costResource: ResourceTarget.FOOD,
      costPerStack: -45,
    },
    decree: {
      name: "Bless the Hearths",
      type: DecreeType.POPULATION_CAP_BOOST,
      effectAmount: 50,
    },
  },
};

/** Looks up an advisor's display info by stable catalog id. Falls back to a generic placeholder. */
export function getAdvisorDisplayInfo(advisorId: string): AdvisorDisplayInfo {
  return ADVISOR_DISPLAY_CATALOG[advisorId] ?? UNKNOWN_ADVISOR;
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
