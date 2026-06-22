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
 * 50–80g, Modest ≈ 100–150g, Costly ≈ 180–230g.
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
          "The Steward",
          "+60 Production/season. The default first hire — competent, unglamorous, always useful.",
          60,
          new Card(
              "REEVE_ALDWIN",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.PRODUCTION.integer(60))),
          AdvisorGate.NONE,
          new Reform(
              "Audit the Granaries",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.PRODUCTION,
              20,
              ResourceTarget.POPULATION,
              -5),
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
          "The Master of Coin",
          "+15 Gold/season.",
          120,
          new Card(
              "MIRA_VOSS",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.GOLD.integer(15))),
          AdvisorGate.NONE,
          new Reform(
              "Raise the Levy",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.GOLD,
              10,
              ResourceTarget.POPULATION,
              -8),
          new Decree("Mint a Currency", DecreeType.GREAT_WORK_BOOST, "TREASURY", ResourceTarget.GOLD, 15),
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
          "+25 Strength/season. Only appears in the offer pool once BARRACKS is completed.",
          200,
          new Card(
              "SIR_BRAM_HOLT",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.STRENGTH.integer(25))),
          AdvisorGate.BARRACKS_COMPLETED,
          new Reform(
              "Conscript Levies",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.STRENGTH,
              15,
              ResourceTarget.POPULATION,
              -10),
          new Decree(
              "Drill the Garrison", DecreeType.GREAT_WORK_BOOST, "BARRACKS", ResourceTarget.STRENGTH, 20),
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
          "+80 Food/season.",
          70,
          new Card(
              "OLD_YARROW",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.FOOD.integer(80))),
          AdvisorGate.NONE,
          new Reform(
              "Stock the Apothecary",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.FOOD,
              25,
              ResourceTarget.PRODUCTION,
              -15),
          new Decree("Found the Almshouse", DecreeType.POPULATION_CAP_BOOST, null, null, 15),
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
          "+50 Production/season. Only appears once at least one Great Work has been commissioned.",
          110,
          new Card(
              "TOMAS_FERRO",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.PRODUCTION.integer(50))),
          AdvisorGate.ANY_GREAT_WORK_COMMISSIONED,
          new Reform(
              "Stockpile Tools",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.PRODUCTION,
              20,
              ResourceTarget.GOLD,
              -10),
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
          "The Spymaster",
          "Reveals one extra candidate in every ADD-phase offer while seated.",
          220,
          new Card("LADY_ISEULT_VARN", 0.0, 0.0, FrequencyType.RECURRING, List.of()),
          AdvisorGate.NONE,
          new Reform(
              "Pay Her Network",
              1,
              ReformBenefitType.EXTRA_ADD_CANDIDATE,
              null,
              1,
              ResourceTarget.GOLD,
              -15),
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
          "+6 Population/season.",
          90,
          new Card(
              "BROTHER_CASSIAN",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.POPULATION.integer(6))),
          AdvisorGate.NONE,
          new Reform(
              "Hold a Sermon of Thanks",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.POPULATION,
              4,
              ResourceTarget.FOOD,
              -40),
          new Decree(
              "Consecrate the Reservoir",
              DecreeType.GREAT_WORK_BOOST,
              "THE_GREAT_RESERVOIR",
              ResourceTarget.FOOD,
              200),
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
          "+90 Production/season.",
          65,
          new Card(
              "GARRICK_STONEBACK",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.PRODUCTION.integer(90))),
          AdvisorGate.NONE,
          new Reform(
              "Open a New Vein",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.PRODUCTION,
              30,
              ResourceTarget.FOOD,
              -20),
          new Decree(
              "Open the Royal Quarry", DecreeType.PASSIVE_BOOST, null, ResourceTarget.PRODUCTION, 35),
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
          "+12 Gold/season. Only appears once THE_GREAT_ROAD has been commissioned.",
          130,
          new Card(
              "DAME_PETRA_LYSK",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.GOLD.integer(12))),
          AdvisorGate.GREAT_ROAD_COMMISSIONED,
          new Reform(
              "Chart New Routes",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.GOLD,
              8,
              ResourceTarget.PRODUCTION,
              -15),
          new Decree(
              "Charter the Trade Road",
              DecreeType.GREAT_WORK_BOOST,
              "THE_GREAT_ROAD",
              ResourceTarget.GOLD,
              300),
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
          "+70 Food/season.",
          60,
          new Card(
              "WENDEL_ASHGROVE",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.FOOD.integer(70))),
          AdvisorGate.NONE,
          new Reform(
              "Clear New Fields",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.FOOD,
              25,
              ResourceTarget.PRODUCTION,
              -20),
          new Decree("Settle the Old Groves", DecreeType.POPULATION_CAP_BOOST, null, null, 40),
          0,
          0,
          0,
          "green",
          "wendel_ashgrove_placeholder");

  public static final Advisor MAESTER_JORIC =
      new Advisor(
          "MAESTER_JORIC",
          "Maester Joric",
          "The Royal Archivist",
          "The first reroll each season is free while he's seated.",
          140,
          new Card("MAESTER_JORIC", 0.0, 0.0, FrequencyType.RECURRING, List.of()),
          AdvisorGate.NONE,
          new Reform(
              "Expand the Archive",
              1,
              ReformBenefitType.FREE_REROLL,
              null,
              1,
              ResourceTarget.GOLD,
              -20),
          new Decree(
              "Catalogue the Treasury",
              DecreeType.GREAT_WORK_BOOST,
              "TREASURY",
              ResourceTarget.PRODUCTION,
              50),
          0,
          1,
          0,
          "violet",
          "maester_joric_placeholder");

  public static final Advisor BRYNN_OSTLAR =
      new Advisor(
          "BRYNN_OSTLAR",
          "Brynn Ostlar",
          "The Master of Horse",
          "Boosts the first Production-producing card on the active season's table by +10 each"
              + " season.",
          210,
          new Card("BRYNN_OSTLAR", 0.0, 0.0, FrequencyType.RECURRING, List.of()),
          AdvisorGate.NONE,
          new Reform(
              "Breed Finer Stock",
              3,
              ReformBenefitType.CROSS_TABLE_BOOST,
              null,
              5,
              ResourceTarget.FOOD,
              -20),
          new Decree("Build the Stables", DecreeType.PASSIVE_BOOST, null, ResourceTarget.PRODUCTION, 40),
          10,
          0,
          0,
          "slate-grey",
          "brynn_ostlar_placeholder");

  public static final Advisor HEWETT_CRAY =
      new Advisor(
          "HEWETT_CRAY",
          "Hewett Cray",
          "The Master of Works",
          "+55 Production/season.",
          100,
          new Card(
              "HEWETT_CRAY",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.PRODUCTION.integer(55))),
          AdvisorGate.NONE,
          new Reform(
              "Commission a Survey",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.PRODUCTION,
              20,
              ResourceTarget.GOLD,
              -12),
          new Decree("Raise New Wards", DecreeType.POPULATION_CAP_BOOST, null, null, 75),
          0,
          0,
          0,
          "slate-grey",
          "hewett_cray_placeholder");

  public static final Advisor CAPTAIN_YSEULT_MARROW =
      new Advisor(
          "CAPTAIN_YSEULT_MARROW",
          "Captain Yseult Marrow",
          "Captain of the Watch",
          "+50 Production/season. Available from the start of a run — the early-game counterpart"
              + " to Sir Bram Holt.",
          85,
          new Card(
              "CAPTAIN_YSEULT_MARROW",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.PRODUCTION.integer(50))),
          AdvisorGate.NONE,
          new Reform(
              "Drill the Watch",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.PRODUCTION,
              15,
              ResourceTarget.POPULATION,
              -6),
          new Decree("Walk the Walls", DecreeType.PASSIVE_BOOST, null, ResourceTarget.PRODUCTION, 30),
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
          "+8 Population/season.",
          75,
          new Card(
              "MOTHER_BRASK",
              0.0,
              0.0,
              FrequencyType.RECURRING,
              List.of(ResourceTarget.POPULATION.integer(8))),
          AdvisorGate.NONE,
          new Reform(
              "Open the Birthing House",
              3,
              ReformBenefitType.CARD_EFFECT,
              ResourceTarget.POPULATION,
              5,
              ResourceTarget.FOOD,
              -45),
          new Decree("Bless the Hearths", DecreeType.POPULATION_CAP_BOOST, null, null, 50),
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
