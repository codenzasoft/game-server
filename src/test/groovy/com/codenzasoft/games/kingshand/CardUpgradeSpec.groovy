package com.codenzasoft.games.kingshand

import com.codenzasoft.games.common.Player
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

/**
 * Upgrade-system behaviour: the fix for cards becoming {@code UNKNOWN CARD} beyond level III.
 *
 * <p>The upgrade level is now the numeric {@link Card#level()} rather than a " II"/" III" suffix
 * mangled into {@link Card#name()}, so a card stays the same stable identity at every level and can
 * upgrade without bound. {@link CardUpgrades} is the single source of truth for how effects and
 * requirements scale, distinguishing single-use from recurring cards.
 *
 * <p>Reference values (base → level N):
 * <ul>
 *   <li>SEEDS (SINGLE): staff 15, prod 50, FOOD +75
 *   <li>FARM (RECURRING): staff 25, prod 90, FOOD +50
 * </ul>
 */
class CardUpgradeSpec extends Specification {

    private static final Player TEST_PLAYER = new Player(UUID.randomUUID(), "test")

    private static GameEngine engine(GameState state) {
        new GameEngine(UUID.randomUUID(), TEST_PLAYER, state)
    }

    private static double food(Card c) {
        c.effects().find { it.target() == ResourceTarget.FOOD }.modifier()
    }

    // ───────────────────────── single-use vs recurring scaling ─────────────────────────

    def "single-use card upgrades from I to II (SEEDS)"() {
        when:
        def two = CardCatalog.SEEDS.mergeWith(CardCatalog.SEEDS)

        then:
        two.name() == "SEEDS"          // stable identity
        two.level() == 2
        food(two) == 150.0             // 75 → 150
        two.staffRequirement() == 22.0 // floor(15 * 3/2)
        two.productionRequirement() == 75.0 // SINGLE escalates: floor(50 * 3/2)
    }

    def "single-use card upgrades from III to IV without becoming UNKNOWN"() {
        given:
        def three = CardUpgrades.calculateCardAtLevel(CardCatalog.SEEDS, 3)

        when:
        def four = three.mergeWith(CardCatalog.SEEDS)

        then:
        four.name() == "SEEDS"
        four.level() == 4
        food(four) == 300.0            // 75 × 4
        four.staffRequirement() == 37.0    // floor(15 * 5/2)
        four.productionRequirement() == 125.0 // floor(50 * 5/2)
    }

    def "single-use card upgrades from IV to V"() {
        given:
        def four = CardUpgrades.calculateCardAtLevel(CardCatalog.SEEDS, 4)

        when:
        def five = four.mergeWith(CardCatalog.SEEDS)

        then:
        five.level() == 5
        food(five) == 375.0
        five.staffRequirement() == 45.0
        five.productionRequirement() == 150.0
    }

    def "recurring card upgrades from I to II (FARM)"() {
        when:
        def two = CardCatalog.FARM.mergeWith(CardCatalog.FARM)

        then:
        two.name() == "FARM"
        two.level() == 2
        food(two) == 100.0
        two.staffRequirement() == 37.0     // floor(25 * 3/2)
        two.productionRequirement() == 90.0 // RECURRING keeps the flat base cost
    }

    def "recurring card upgrades from III to IV without becoming UNKNOWN"() {
        given:
        def three = CardUpgrades.calculateCardAtLevel(CardCatalog.FARM, 3)

        when:
        def four = three.mergeWith(CardCatalog.FARM)

        then:
        four.name() == "FARM"
        four.level() == 4
        food(four) == 200.0
        four.staffRequirement() == 62.0    // floor(25 * 5/2)
        four.productionRequirement() == 90.0 // still flat
    }

    def "recurring card upgrades from IV to V"() {
        given:
        def four = CardUpgrades.calculateCardAtLevel(CardCatalog.FARM, 4)

        when:
        def five = four.mergeWith(CardCatalog.FARM)

        then:
        five.level() == 5
        food(five) == 250.0
        five.staffRequirement() == 75.0
        five.productionRequirement() == 90.0
    }

    def "single-use and recurring production requirements scale by different rules"() {
        given:
        def seeds3 = CardUpgrades.calculateCardAtLevel(CardCatalog.SEEDS, 3) // SINGLE
        def farm3 = CardUpgrades.calculateCardAtLevel(CardCatalog.FARM, 3)   // RECURRING

        expect:
        // Single-use production escalates with level; recurring production stays flat at base.
        seeds3.productionRequirement() == 100.0
        seeds3.productionRequirement() != CardCatalog.SEEDS.productionRequirement()
        farm3.productionRequirement() == CardCatalog.FARM.productionRequirement()
    }

    // ───────────────────────── multiple effects ─────────────────────────

    def "a recurring card with multiple effects upgrades every effect"() {
        // HUNTING_CAMP: FOOD +60, PRODUCTION +80 (frequency is per-card, so both are recurring)
        when:
        def four = CardUpgrades.calculateCardAtLevel(CardCatalog.HUNTING_CAMP, 4)

        then:
        four.effects().size() == 2
        four.effects().find { it.target() == ResourceTarget.FOOD }.modifier() == 240.0
        four.effects().find { it.target() == ResourceTarget.PRODUCTION }.modifier() == 320.0
        four.staffRequirement() == 187.0    // floor(75 * 5/2)
        four.productionRequirement() == 400.0 // recurring: flat
    }

    def "a single-use card with multiple effects upgrades every effect"() {
        // SETTLERS: POPULATION_CAP +50, POPULATION +75, SINGLE
        when:
        def two = CardCatalog.SETTLERS.mergeWith(CardCatalog.SETTLERS)

        then:
        two.effects().size() == 2
        two.effects().find { it.target() == ResourceTarget.POPULATION_CAP }.modifier() == 100.0
        two.effects().find { it.target() == ResourceTarget.POPULATION }.modifier() == 150.0
        two.productionRequirement() == 75.0 // SINGLE escalates: floor(50 * 3/2)
    }

    def "upgrading preserves the number of effects at high levels"() {
        expect:
        CardUpgrades.calculateCardAtLevel(CardCatalog.HUNTING_CAMP, 12).effects().size() == 2
    }

    // ───────────────────────── identity, mutation, drift, overflow ─────────────────────────

    def "a level-IV card keeps the stable base card ID"() {
        expect:
        CardUpgrades.calculateCardAtLevel(CardCatalog.FARM, 4).name() == "FARM"
    }

    def "a very high level stays a recognised card (never UNKNOWN)"() {
        when:
        def ten = CardUpgrades.calculateCardAtLevel(CardCatalog.FARM, 10)

        then:
        ten.name() == "FARM"     // texture + display both resolve from this stable id
        ten.level() == 10
    }

    def "upgrading does not mutate the base card definition"() {
        given:
        def baseFoodBefore = food(CardCatalog.FARM)

        when:
        CardCatalog.FARM.mergeWith(CardCatalog.FARM)

        then:
        CardCatalog.FARM.level() == 1
        food(CardCatalog.FARM) == baseFoodBefore
    }

    def "repeatedly merging accumulates no rounding drift vs a direct level calculation"() {
        given:
        // Chain five single upgrades (the in-game path) …
        def chained = CardCatalog.FARM
        (1..4).each { chained = chained.mergeWith(CardCatalog.FARM) }
        // … and compute the same level straight from the base definition.
        def direct = CardUpgrades.calculateCardAtLevel(CardCatalog.FARM, 5)

        expect:
        chained.level() == 5
        chained.staffRequirement() == direct.staffRequirement()      // 75.0, no floor drift
        chained.productionRequirement() == direct.productionRequirement()
        food(chained) == food(direct)                                // 250.0
    }

    def "a high but realistic upgrade level does not overflow or throw"() {
        when:
        def huge = CardUpgrades.calculateCardAtLevel(CardCatalog.FARM, 1000)

        then:
        noExceptionThrown()
        huge.level() == 1000
        huge.name() == "FARM"
        food(huge) == 50_000.0
        CardUpgrades.roman(1000) == "M"
        CardUpgrades.roman(5000) == "5000" // beyond classic range → integer fallback, still safe
    }

    // ───────────────────────── serialization ─────────────────────────

    def "serialization preserves a level greater than III"() {
        given:
        def five = CardUpgrades.calculateCardAtLevel(CardCatalog.FARM, 5)
        def json = new ObjectMapper().writeValueAsString(five)

        expect:
        json.contains('"name":"FARM"')   // stable identity on the wire
        json.contains('"level":5')       // numeric level travels to the client
    }

    // ───────────────────────── backward compatibility ─────────────────────────

    def "cards built via the pre-level constructors default to level 1 (legacy load)"() {
        expect:
        // 5-arg (normal card) and 10-arg (great work) convenience constructors both default level 1.
        new Card("X", 1, 1, FrequencyType.SINGLE, [ResourceTarget.FOOD.integer(5)]).level() == 1
        CardCatalog.TREASURY_CONSTRUCTION.level() == 1
    }

    def "roman/display formatting supports arbitrary levels without affecting identity"() {
        expect:
        CardUpgrades.roman(level) == roman
        CardUpgrades.displayName("Farm", level) == display

        where:
        level | roman   | display
        1     | "I"     | "Farm"
        4     | "IV"    | "Farm IV"
        7     | "VII"   | "Farm VII"
        9     | "IX"    | "Farm IX"
        14    | "XIV"   | "Farm XIV"
    }

    // ───────────────────────── engine reproduction (end-to-end) ─────────────────────────

    def "placing FARM repeatedly upgrades past level III with a stable id (bug reproduction)"() {
        given:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(25)
                .production(5000)
                .table(Season.SPRING, [CardCatalog.FARM])
                .hand([CardCatalog.FARM, CardCatalog.FARM, CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when: "three more FARMs are placed onto the table FARM (→ level 4)"
        3.times { engine.placeCard(CardCatalog.FARM) }

        then:
        def table = engine.state.getPlayer(0).board.get(Season.SPRING)
        table.size() == 1                 // recurring cards never open a new slot
        def farm = table.get(0)
        farm.name() == "FARM"             // pre-fix this was "FARM III II" → UNKNOWN CARD
        farm.level() == 4
        food(farm) == 200.0
    }

    def "a recurring MINE folds a MINE II on the table up to MINE III"() {
        given:
        def mineTwo = CardUpgrades.calculateCardAtLevel(CardCatalog.MINE, 2)
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(50)
                .production(5000)
                .table(Season.SPRING, [mineTwo])
                .hand([CardCatalog.MINE])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.MINE)

        then:
        def mine = engine.state.getPlayer(0).board.get(Season.SPRING).get(0)
        mine.name() == "MINE"
        mine.level() == 3
    }

    def "a fresh single-use card does not fold into an already-upgraded copy (new slot instead)"() {
        given:
        def seedsTwo = CardUpgrades.calculateCardAtLevel(CardCatalog.SEEDS, 2)
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(25)
                .production(5000)
                .table(Season.SPRING, [seedsTwo])
                .hand([CardCatalog.SEEDS])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.SEEDS)

        then: "the base SEEDS takes its own slot rather than lifting SEEDS II to III"
        def table = engine.state.getPlayer(0).board.get(Season.SPRING)
        table.size() == 2
        table.count { it.level() == 2 } == 1
        table.count { it.level() == 1 } == 1
    }
}
