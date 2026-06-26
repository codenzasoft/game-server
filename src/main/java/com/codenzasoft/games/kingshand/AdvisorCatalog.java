package com.codenzasoft.games.kingshand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static catalog of all 15 King's Hand advisors, translated directly from {@code
 * kingshand-advisors-design.md}. Roughly 5x the council's 3 seats on purpose — a given run will
 * never see all 15, and passing on one during COUNCIL_ADD doesn't remove it from later offers.
 *
 * <p>Hiring-cost tiers are pitched against TREASURY's completed output (40 gold/season): Cheap ≈
 * 75–120g, Modest ≈ 150–225g, Costly ≈ 270–345g.
 *
 * <p>Advisor portraits are not yet final art — {@link Advisor#portraitPlaceholderId()} and {@link
 * Advisor#ringColor()} give the client enough to render a consistent placeholder (a colored ring
 * around a generic profile circle) until real per-advisor art is dropped in.
 */
public final class AdvisorCatalog {

  private AdvisorCatalog() {}

  public static final Advisor REEVE_ALDWIN =
      new Advisor(
          "REEVE_ALDWIN",
          "Reeve Aldwin",
          "The Overseer",
          "Competent and unglamorous, Aldwin keeps the granaries running and the ledgers balanced"
              + " without ever needing to be asked twice.",
          180,
          new Card(
              "REEVE_ALDWIN",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.PRODUCTION.integer(90))),
          AdvisorGate.NONE,
          new Reform(
              "Audit the Granaries",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.PRODUCTION,
              90,
              ResourceTarget.POPULATION,
              -45),
          new Decree("Found the Town Hall", DecreeType.FREE_REROLL, null, null, 1),
          0,
          0,
          0,
          "slate-grey",
          "reeve_aldwin_placeholder");

  public static final Advisor MIRA_VOSS =
      new Advisor(
          "MIRA_VOSS",
          "Mira Voss",
          "The Treasurer",
          "Sharp with numbers and sharper in a negotiation, Voss keeps the coffers fuller than"
              + " anyone expects.",
          360,
          new Card(
              "MIRA_VOSS",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.GOLD.integer(38))),
          AdvisorGate.NONE,
          new Reform(
              "Raise the Levy",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.GOLD,
              38,
              ResourceTarget.POPULATION,
              -38),
          new Decree(
              "Mint a Currency", DecreeType.GREAT_WORK_BOOST, "TREASURY", ResourceTarget.GOLD, 23),
          0,
          0,
          0,
          "amber",
          "mira_voss_placeholder");

  public static final Advisor SIR_BRAM_HOLT =
      new Advisor(
          "SIR_BRAM_HOLT",
          "Sir Bram Holt",
          "The Lord Marshal",
          "A grizzled veteran who drills the garrison hard and trusts results over rank.",
          300,
          new Card(
              "SIR_BRAM_HOLT",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.STRENGTH.integer(38))),
          AdvisorGate.BARRACKS_COMPLETED,
          new Reform(
              "Conscript Levies",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.STRENGTH,
              23,
              ResourceTarget.POPULATION,
              -15),
          new Decree(
              "Drill the Garrison",
              DecreeType.GREAT_WORK_BOOST,
              "BARRACKS",
              ResourceTarget.STRENGTH,
              30),
          0,
          0,
          0,
          "deep-red",
          "sir_bram_holt_placeholder");

  public static final Advisor OLD_YARROW =
      new Advisor(
          "OLD_YARROW",
          "Old Yarrow",
          "The Court Physician",
          "A blunt, no-nonsense healer who's seen every fever and famine the realm can offer, and"
              + " still keeps the larders full.",
          105,
          new Card(
              "OLD_YARROW",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.FOOD.integer(120))),
          AdvisorGate.NONE,
          new Reform(
              "Stock the Apothecary",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.FOOD,
              38,
              ResourceTarget.PRODUCTION,
              -23),
          new Decree("Found the Almshouse", DecreeType.POPULATION_CAP_BOOST, null, null, 23),
          0,
          0,
          0,
          "green",
          "old_yarrow_placeholder");

  public static final Advisor TOMAS_FERRO =
      new Advisor(
          "TOMAS_FERRO",
          "Tomas Ferro",
          "The Master Engineer",
          "A tireless tinkerer drawn to the scaffolding of any grand project, always certain the"
              + " next design will be sturdier than the last.",
          165,
          new Card(
              "TOMAS_FERRO",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.PRODUCTION.integer(75))),
          AdvisorGate.ANY_GREAT_WORK_COMMISSIONED,
          new Reform(
              "Stockpile Tools",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.PRODUCTION,
              30,
              ResourceTarget.GOLD,
              -15),
          new Decree("Expedite Construction", DecreeType.GREAT_WORK_ACCELERATE, null, null, 1),
          0,
          0,
          0,
          "slate-grey",
          "tomas_ferro_placeholder");

  public static final Advisor LADY_ISEULT_VARN =
      new Advisor(
          "LADY_ISEULT_VARN",
          "Lady Iseult Varn",
          "The Broker",
          "Keeps a network of informants that always turns up one more name worth considering"
              + " when the court convenes.",
          330,
          new Card("LADY_ISEULT_VARN", 0.0, 0.0, FrequencyType.RECURRING, List.of()),
          AdvisorGate.NONE,
          new Reform(
              "Pay Her Network",
              1,
              ReformBenefitType.EXTRA_ADD_CANDIDATE,
              null,
              1,
              ResourceTarget.GOLD,
              -23),
          new Decree("Plant an Informant", DecreeType.FREE_REROLL, null, null, 1),
          0,
          0,
          1,
          "violet",
          "lady_iseult_varn_placeholder");

  public static final Advisor BROTHER_CASSIAN =
      new Advisor(
          "BROTHER_CASSIAN",
          "Brother Cassian",
          "The High Chaplain",
          "A gentle, steady presence who tends to the sick and newly born alike, and always seems"
              + " to know just what to say.",
          135,
          new Card(
              "BROTHER_CASSIAN",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.POPULATION.integer(9))),
          AdvisorGate.NONE,
          new Reform(
              "Hold a Sermon of Thanks",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.POPULATION,
              6,
              ResourceTarget.FOOD,
              -60),
          new Decree(
              "Consecrate the Reservoir",
              DecreeType.GREAT_WORK_BOOST,
              "THE_GREAT_RESERVOIR",
              ResourceTarget.FOOD,
              300),
          0,
          0,
          0,
          "dusty-blue",
          "brother_cassian_placeholder");

  public static final Advisor GARRICK_STONEBACK =
      new Advisor(
          "GARRICK_STONEBACK",
          "Garrick Stoneback",
          "The Quarrymaster",
          "Built like the stone he cuts, Stoneback has never met a vein of rock he couldn't crack"
              + " open.",
          98,
          new Card(
              "GARRICK_STONEBACK",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.PRODUCTION.integer(135))),
          AdvisorGate.NONE,
          new Reform(
              "Open a New Vein",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.PRODUCTION,
              45,
              ResourceTarget.FOOD,
              -30),
          new Decree(
              "Open the Royal Quarry",
              DecreeType.PASSIVE_BOOST,
              null,
              ResourceTarget.PRODUCTION,
              53),
          0,
          0,
          0,
          "slate-grey",
          "garrick_stoneback_placeholder");

  public static final Advisor DAME_PETRA_LYSK =
      new Advisor(
          "DAME_PETRA_LYSK",
          "Dame Petra Lysk",
          "The Harbor Mistress",
          "A shrewd negotiator who knows every merchant captain in port by name, and exactly how"
              + " much they owe her.",
          195,
          new Card(
              "DAME_PETRA_LYSK",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.GOLD.integer(18))),
          AdvisorGate.GREAT_ROAD_COMMISSIONED,
          new Reform(
              "Chart New Routes",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.GOLD,
              12,
              ResourceTarget.PRODUCTION,
              -23),
          new Decree(
              "Charter the Trade Road",
              DecreeType.GREAT_WORK_BOOST,
              "THE_GREAT_ROAD",
              ResourceTarget.GOLD,
              450),
          0,
          0,
          0,
          "amber",
          "dame_petra_lysk_placeholder");

  public static final Advisor WENDEL_ASHGROVE =
      new Advisor(
          "WENDEL_ASHGROVE",
          "Wendel Ashgrove",
          "The Royal Forester",
          "Knows every deer trail and berry patch in the royal wood, and never comes back"
              + " empty-handed.",
          90,
          new Card(
              "WENDEL_ASHGROVE",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.FOOD.integer(105))),
          AdvisorGate.NONE,
          new Reform(
              "Clear New Fields",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.FOOD,
              38,
              ResourceTarget.PRODUCTION,
              -30),
          new Decree("Settle the Old Groves", DecreeType.POPULATION_CAP_BOOST, null, null, 60),
          0,
          0,
          0,
          "green",
          "wendel_ashgrove_placeholder");

  public static final Advisor MAESTER_JORIC =
      new Advisor(
          "MAESTER_JORIC",
          "Joric Wren",
          "The Royal Archivist",
          "A meticulous record-keeper who's always already double-checked your decision before"
              + " you've finished making it, and isn't shy about offering a free second opinion.",
          210,
          new Card("MAESTER_JORIC", 0.0, 0.0, FrequencyType.RECURRING, List.of()),
          AdvisorGate.NONE,
          new Reform(
              "Expand the Archive",
              1,
              ReformBenefitType.FREE_REROLL,
              null,
              1,
              ResourceTarget.GOLD,
              -30),
          new Decree(
              "Catalogue the Treasury",
              DecreeType.GREAT_WORK_BOOST,
              "TREASURY",
              ResourceTarget.PRODUCTION,
              75),
          0,
          1,
          0,
          "violet",
          "maester_joric_placeholder");

  public static final Advisor BRYNN_OSTLAR =
      new Advisor(
          "BRYNN_OSTLAR",
          "Brynn Ostlar",
          "The Stablemaster",
          "Has an eye for good bloodlines and broken wagon wheels alike — whatever in the yard"
              + " needs the most help, she's already there.",
          315,
          new Card("BRYNN_OSTLAR", 0.0, 0.0, FrequencyType.RECURRING, List.of()),
          AdvisorGate.NONE,
          new Reform(
              "Breed Finer Stock",
              3,
              ReformBenefitType.CROSS_TABLE_BOOST,
              null,
              8,
              ResourceTarget.FOOD,
              -30),
          new Decree(
              "Build the Stables", DecreeType.PASSIVE_BOOST, null, ResourceTarget.PRODUCTION, 60),
          15,
          0,
          0,
          "slate-grey",
          "brynn_ostlar_placeholder");

  public static final Advisor HEWETT_CRAY =
      new Advisor(
          "HEWETT_CRAY",
          "Hewett Cray",
          "The Foreman",
          "Runs every worksite like it's behind schedule, because in his mind, it always is.",
          150,
          new Card(
              "HEWETT_CRAY",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.PRODUCTION.integer(83))),
          AdvisorGate.NONE,
          new Reform(
              "Commission a Survey",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.PRODUCTION,
              30,
              ResourceTarget.GOLD,
              -18),
          new Decree("Raise New Wards", DecreeType.POPULATION_CAP_BOOST, null, null, 113),
          0,
          0,
          0,
          "slate-grey",
          "hewett_cray_placeholder");

  public static final Advisor CAPTAIN_YSEULT_MARROW =
      new Advisor(
          "CAPTAIN_YSEULT_MARROW",
          "Captain Yseult Marrow",
          "Captain of the Guard",
          "Keeps the guard sharp and the work crews moving, often by little more than the threat"
              + " of her glare.",
          128,
          new Card(
              "CAPTAIN_YSEULT_MARROW",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.PRODUCTION.integer(75))),
          AdvisorGate.NONE,
          new Reform(
              "Drill the Guard",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.PRODUCTION,
              23,
              ResourceTarget.POPULATION,
              -9),
          new Decree(
              "Walk the Walls", DecreeType.PASSIVE_BOOST, null, ResourceTarget.PRODUCTION, 45),
          0,
          0,
          0,
          "slate-grey",
          "captain_yseult_marrow_placeholder");

  public static final Advisor MOTHER_BRASK =
      new Advisor(
          "MOTHER_BRASK",
          "Mother Brask",
          "Keeper of the Hearth",
          "Runs the household like a small kingdom of her own — fed, warm, and never short of"
              + " advice nobody asked for.",
          113,
          new Card(
              "MOTHER_BRASK",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.POPULATION.integer(12))),
          AdvisorGate.NONE,
          new Reform(
              "Open the Birthing House",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.POPULATION,
              8,
              ResourceTarget.FOOD,
              -68),
          new Decree("Bless the Hearths", DecreeType.POPULATION_CAP_BOOST, null, null, 75),
          0,
          0,
          0,
          "dusty-blue",
          "mother_brask_placeholder");

  /** All 15 advisors, in design-doc order. */
  public static final List<Advisor> ALL =
      List.of(
          REEVE_ALDWIN,
          MIRA_VOSS,
          SIR_BRAM_HOLT,
          OLD_YARROW,
          TOMAS_FERRO,
          LADY_ISEULT_VARN,
          BROTHER_CASSIAN,
          GARRICK_STONEBACK,
          DAME_PETRA_LYSK,
          WENDEL_ASHGROVE,
          MAESTER_JORIC,
          BRYNN_OSTLAR,
          HEWETT_CRAY,
          CAPTAIN_YSEULT_MARROW,
          MOTHER_BRASK);

  private static final Map<String, Advisor> BY_ID = new LinkedHashMap<>();

  static {
    for (Advisor a : ALL) {
      BY_ID.put(a.id(), a);
    }
  }

  /** Looks up an advisor by its stable catalog id (e.g. {@code "REEVE_ALDWIN"}). */
  public static Advisor byId(String id) {
    return BY_ID.get(id);
  }
}
