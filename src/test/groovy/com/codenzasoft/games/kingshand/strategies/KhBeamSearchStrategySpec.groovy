package com.codenzasoft.games.kingshand.strategies

import com.codenzasoft.games.kingshand.CardCatalog
import com.codenzasoft.games.kingshand.GameEngine
import com.codenzasoft.games.kingshand.GameState
import com.codenzasoft.games.kingshand.Phase
import com.codenzasoft.games.kingshand.PlayerState
import com.codenzasoft.games.kingshand.Season
import com.codenzasoft.games.kingshand.TimeState
import spock.lang.Specification

/**
 * Behavioural specs for {@link KhBeamSearchStrategy} — the survival/economy/offense decisions the
 * brief calls out. States are built through the real {@link PlayerState}/{@link GameState} builders
 * and decisions run through the real {@link GameEngine}; nothing about the rules is mocked away.
 */
class KhBeamSearchStrategySpec extends Specification {

    private static final long SEED = 20260804L

    private static KhBeamSearchStrategy bot() {
        new KhBeamSearchStrategy(KhBotConfig.defaults())
    }

    private static GameEngine engineFor(GameState state) {
        new GameEngine(state, new Random(SEED))
    }

    /** Single-seat PLAY state with the given spring table, hand, and resources. */
    private static GameState play(Map a) {
        def player = PlayerState.builder()
                .population((a.pop ?: 100.0) as double)
                .populationCap((a.cap ?: 100.0) as double)
                .food((a.food ?: 750.0) as double)
                .production((a.production ?: 1000.0) as double)
                .strengthUnlocked((a.strengthUnlocked ?: false) as boolean)
                .table(Season.SPRING, (a.table ?: []) as List)
                .hand((a.hand ?: []) as List)
                .build()
        GameState.multiPlayer([player], new TimeState(Season.SPRING, Phase.PLAY, 1))
    }

    /** Single-seat ADD state offering the given cards. */
    private static GameState add(Map a) {
        def player = PlayerState.builder()
                .population((a.pop ?: 100.0) as double)
                .populationCap((a.cap ?: 100.0) as double)
                .food((a.food ?: 750.0) as double)
                .production((a.production ?: 1000.0) as double)
                .addChoices((a.choices ?: []) as List)
                .build()
        GameState.multiPlayer([player], new TimeState(Season.SPRING, Phase.ADD, 1))
    }

    private static def resources(GameState s) { s.getPlayer(0).getResources() }

    // ---------------------------------------------------------------------------------
    // 1. One upkeep from starvation → takes the available food solution.
    // ---------------------------------------------------------------------------------
    def "places a food card when one upkeep from starvation"() {
        setup: "food 60 < spring upkeep 100 — starves unless a food card is played"
        def state = play(pop: 100.0, food: 60.0, production: 400.0,
                hand: [CardCatalog.MINE, CardCatalog.SEEDS])
        def engine = engineFor(state)

        when:
        engine.setActiveSeat(0)
        bot().act(engine, 0)

        then: "the kingdom survives the upkeep rather than starving down toward 80"
        !state.getPlayer(0).isGameOver()
        resources(state).population() >= 95.0
    }

    // ---------------------------------------------------------------------------------
    // 2. Refuses growth that predictably causes starvation.
    // ---------------------------------------------------------------------------------
    def "refuses population growth that would cause starvation"() {
        setup: "cheap SETTLERS (+75 pop) would blow past a food supply that only just covers upkeep"
        def state = play(pop: 100.0, cap: 100.0, food: 110.0, production: 300.0,
                hand: [CardCatalog.SETTLERS, CardCatalog.FARM])
        def engine = engineFor(state)

        when:
        engine.setActiveSeat(0)
        bot().act(engine, 0)

        then: "population is not inflated by SETTLERS"
        resources(state).population() <= 105.0
    }

    // ---------------------------------------------------------------------------------
    // 3. Accepts growth when food can safely support it.
    // ---------------------------------------------------------------------------------
    def "accepts population growth when food can support it"() {
        setup: "ample food + a recurring farm — growing the workforce is safe here"
        def state = play(pop: 100.0, cap: 200.0, food: 5000.0, production: 500.0,
                table: [], hand: [CardCatalog.SETTLERS, CardCatalog.FARM])
        def engine = engineFor(state)

        when:
        engine.setActiveSeat(0)
        bot().act(engine, 0)

        then: "the bot grows the population when it is clearly affordable"
        resources(state).population() > 100.0
    }

    // ---------------------------------------------------------------------------------
    // 4. Prefers recurring food over a weak offensive play when survival is unsafe.
    // ---------------------------------------------------------------------------------
    def "prefers recurring food over a weak attack when unsafe"() {
        setup: "no recurring food, thin reserve — FARM addresses the weakness, WARBAND does nothing"
        def state = play(pop: 100.0, food: 150.0, production: 300.0, strengthUnlocked: true,
                hand: [CardCatalog.FARM, CardCatalog.WARBAND])
        def engine = engineFor(state)

        when:
        engine.setActiveSeat(0)
        bot().act(engine, 0)

        then: "the recurring FARM is on the table (it stays; the attack was not chosen)"
        state.getPlayer(0).getTable(Season.SPRING).collect { it.name() }.contains("FARM")
    }

    // ---------------------------------------------------------------------------------
    // 5. Invests in production when food is stable but production matters.
    // ---------------------------------------------------------------------------------
    def "invests in a production engine when food is stable"() {
        setup: "huge food reserve, unneeded extra food card vs a recurring production mine"
        def state = play(pop: 100.0, food: 5000.0, production: 400.0,
                hand: [CardCatalog.MINE, CardCatalog.SEEDS])
        def engine = engineFor(state)

        when:
        engine.setActiveSeat(0)
        bot().act(engine, 0)

        then: "the recurring production MINE is built"
        state.getPlayer(0).getTable(Season.SPRING).collect { it.name() }.contains("MINE")
    }

    // ---------------------------------------------------------------------------------
    // 6. Passes when every legal play makes the position worse.
    // ---------------------------------------------------------------------------------
    def "passes when the only playable card is harmful"() {
        setup: "two farms make pop 100 sustainable; the only hand card (SETTLERS) would break that"
        def state = play(pop: 100.0, cap: 100.0, food: 200.0, production: 300.0,
                table: [CardCatalog.FARM, CardCatalog.FARM],
                hand: [CardCatalog.SETTLERS])
        def engine = engineFor(state)

        when:
        engine.setActiveSeat(0)
        bot().act(engine, 0)

        then: "SETTLERS was not played — population is not inflated"
        resources(state).population() <= 100.0
    }

    // ---------------------------------------------------------------------------------
    // 7. Takes a safe lethal attack over further development.
    // ---------------------------------------------------------------------------------
    def "takes a lethal attack that ends the game"() {
        setup: "opponent at population 20 with no standing army; WARBAND deals 25"
        def me = PlayerState.builder()
                .population(100.0).food(500.0).production(300.0).strengthUnlocked(true)
                .hand([CardCatalog.WARBAND]).build()
        def opp = PlayerState.builder()
                .population(20.0).food(100.0).production(100.0).build()
        def state = GameState.multiPlayer([me, opp], new TimeState(Season.SPRING, Phase.PLAY, 1))
        def engine = engineFor(state)

        when: "the bot acts, then the opponent ends their turn so the season resolves"
        engine.setActiveSeat(0)
        bot().act(engine, 0)
        engine.setActiveSeat(1)
        engine.endAndRun()

        then: "the opponent is knocked out and the bot wins by conquest"
        state.getPlayer(1).isGameOver()
        state.winnerSeat() == 0
    }

    // ---------------------------------------------------------------------------------
    // 8. Drafts the survival-critical card over a strong-but-unusable one.
    // ---------------------------------------------------------------------------------
    def "drafts food over an unaffordable production card when starving"() {
        setup: "starving, and MINE (300) is unaffordable at 150 production"
        def state = add(pop: 100.0, food: 50.0, production: 150.0,
                choices: [CardCatalog.MINE, CardCatalog.SEEDS])
        def engine = engineFor(state)

        when:
        engine.setActiveSeat(0)
        bot().act(engine, 0)

        then: "SEEDS entered the deck; MINE did not"
        state.getPlayer(0).isCardInPlay("SEEDS")
        !state.getPlayer(0).isCardInPlay("MINE")
    }

    // ---------------------------------------------------------------------------------
    // 9. Does not draft a card it is unlikely to afford within a reasonable horizon.
    // ---------------------------------------------------------------------------------
    def "does not draft a card it cannot afford, preferring an affordable useful one"() {
        setup: "low production, unsafe food; FARM (90) is affordable and useful, MINE (300) is not"
        def state = add(pop: 100.0, food: 150.0, production: 100.0,
                choices: [CardCatalog.MINE, CardCatalog.FARM])
        def engine = engineFor(state)

        when:
        engine.setActiveSeat(0)
        bot().act(engine, 0)

        then:
        state.getPlayer(0).isCardInPlay("FARM")
        !state.getPlayer(0).isCardInPlay("MINE")
    }

    // ---------------------------------------------------------------------------------
    // 10. Deterministic given the same state and seed.
    // ---------------------------------------------------------------------------------
    def "produces identical decisions for identical state and seed"() {
        setup:
        def stateA = play(pop: 100.0, food: 200.0, production: 500.0,
                hand: [CardCatalog.FARM, CardCatalog.MINE, CardCatalog.SEEDS])
        def stateB = play(pop: 100.0, food: 200.0, production: 500.0,
                hand: [CardCatalog.FARM, CardCatalog.MINE, CardCatalog.SEEDS])

        when:
        def eA = engineFor(stateA)
        eA.setActiveSeat(0)
        new KhBeamSearchStrategy(KhBotConfig.defaults()).act(eA, 0)
        def eB = engineFor(stateB)
        eB.setActiveSeat(0)
        new KhBeamSearchStrategy(KhBotConfig.defaults()).act(eB, 0)

        then: "both runs reach the same resources and the same spring table"
        resources(stateA) == resources(stateB)
        stateA.getPlayer(0).getTable(Season.SPRING).collect { it.name() } ==
                stateB.getPlayer(0).getTable(Season.SPRING).collect { it.name() }
    }

    // ---------------------------------------------------------------------------------
    // 12. Completes a decision within a reasonable time budget.
    // ---------------------------------------------------------------------------------
    def "completes a PLAY decision quickly"() {
        setup:
        def state = play(pop: 100.0, food: 300.0, production: 800.0,
                table: [CardCatalog.FARM, CardCatalog.FERTILIZER],
                hand: [CardCatalog.MINE, CardCatalog.SEEDS, CardCatalog.HUNTING_CAMP])
        def engine = engineFor(state)

        when:
        engine.setActiveSeat(0)
        def start = System.nanoTime()
        bot().act(engine, 0)
        def elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        then:
        elapsedMs < 1000.0
    }
}
