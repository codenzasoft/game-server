package com.codenzasoft.games.kingshand

import com.codenzasoft.games.common.GameLifecycleObserver
import com.codenzasoft.games.common.Player
import com.codenzasoft.games.kingshand.strategies.KhHeuristicStrategy
import spock.lang.Specification

import java.util.concurrent.Executor

/**
 * Multiplayer behavior tests: the readiness barrier (KH-MULTIPLAYER-PLAN Slice 1), bot driving
 * (Slice 2), sabotage's snapshot resolution (Slice 3), and forfeit / match outcome (Slice 4).
 *
 * <p>{@link GameState}/{@link PlayerState} are immutable: the engine swaps in new instances, so
 * post-action assertions read {@code engine.getState()} rather than the local setup reference.
 */
class GameEngineBarrierSpec extends Specification {

    private static PlayerState kingdom(Map args = [:]) {
        def b = PlayerState.builder()
        if (args.population != null) b.population(args.population as double)
        if (args.production != null) b.production(args.production as double)
        if (args.food != null) b.food(args.food as double)
        if (args.strength != null) b.strength(args.strength as double)
        if (args.strengthUnlocked) b.strengthUnlocked(true)
        if (args.table != null) b.table(args.season as Season, args.table as List<Card>)
        b.build()
    }

    private static GameState twoSeats(Phase phase, Season season, PlayerState k0, PlayerState k1) {
        GameState.multiPlayer([k0, k1], new TimeState(season, phase, 1))
    }

    // ----- GameState: submit bookkeeping -----

    def "allSubmitted requires every live seat and ignores knocked-out seats"() {
        setup:
        def state = twoSeats(Phase.PLAY, Season.SPRING, kingdom(), kingdom())

        expect:
        !state.allSubmitted()

        when:
        state = state.markSubmitted(0)

        then:
        state.isSubmitted(0)
        !state.allSubmitted()
        state.getSubmittedSeats() == [0]

        when:
        state = state.withPlayer(1, state.getPlayer(1).withForfeited(true))

        then: "a forfeited seat no longer blocks the barrier"
        state.allSubmitted()

        when:
        state = state.clearSubmitted()

        then:
        !state.isSubmitted(0)
        state.getSubmittedSeats() == []
    }

    // ----- Slice 1: the readiness barrier -----

    def "PLAY: first seat's endAndRun marks it submitted without advancing the clock or resolving"() {
        setup:
        def farm = new Card("FARM_T", 0, 0, FrequencyType.RECURRING, [ResourceTarget.PRODUCTION.integer(100)])
        def k0 = kingdom(population: 100, production: 500, food: 750, season: Season.SPRING, table: [farm])
        def k1 = kingdom(population: 100, production: 500, food: 750)
        def state = twoSeats(Phase.PLAY, Season.SPRING, k0, k1)
        def engine = new GameEngine(state, new Random())

        when:
        engine.setActiveSeat(0)
        engine.endAndRun()

        then: "phase unchanged, seat 0 waiting, no table resolved yet"
        with(engine.state) {
            time.phase == Phase.PLAY
            time.season == Season.SPRING
            getSubmittedSeats() == [0]
            getPlayer(0).resources.production() == 500.0d
            getPlayer(0).resolutionLog.isEmpty()
        }
    }

    def "PLAY: second seat's endAndRun resolves both tables but holds the clock until both Continue"() {
        setup:
        def farm0 = new Card("FARM_T", 0, 0, FrequencyType.RECURRING, [ResourceTarget.PRODUCTION.integer(100)])
        def farm1 = new Card("FIELD_T", 0, 0, FrequencyType.RECURRING, [ResourceTarget.PRODUCTION.integer(40)])
        def k0 = kingdom(population: 100, production: 500, food: 750, season: Season.SPRING, table: [farm0])
        def k1 = kingdom(population: 100, production: 500, food: 750, season: Season.SPRING, table: [farm1])
        def state = twoSeats(Phase.PLAY, Season.SPRING, k0, k1)
        def engine = new GameEngine(state, new Random())

        when:
        engine.setActiveSeat(0)
        engine.endAndRun()
        engine.setActiveSeat(1)
        engine.endAndRun()

        then: "both kingdoms resolved; the shared clock is held on the resolved season pending Continue"
        with(engine.state) {
            getPlayer(0).resources.production() == 600.0d
            getPlayer(1).resources.production() == 540.0d
            !getPlayer(0).resolutionLog.isEmpty()
            !getPlayer(1).resolutionLog.isEmpty()
            resolutionPending
            time.phase == Phase.PLAY
            time.season == Season.SPRING
            getSubmittedSeats() == []
        }

        when: "both seats acknowledge the resolution screen"
        engine.setActiveSeat(0)
        engine.continueResolution()
        engine.setActiveSeat(1)
        engine.continueResolution()

        then: "now the shared clock advances exactly once (spring PLAY -> summer ADD) and seeds ADD"
        with(engine.state) {
            !resolutionPending
            time.phase == Phase.ADD
            time.season == Season.SUMMER
            time.year == 1
            getSubmittedSeats() == []
            !getPlayer(0).addChoices.isEmpty()
            !getPlayer(1).addChoices.isEmpty()
        }
    }

    def "a seat cannot submit the same phase twice"() {
        setup:
        def state = twoSeats(Phase.PLAY, Season.SPRING, kingdom(), kingdom())
        def engine = new GameEngine(state, new Random())

        when:
        engine.setActiveSeat(0)
        engine.endAndRun()
        engine.endAndRun()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("already submitted")
    }

    def "a submitted seat can take no further phase actions until the barrier releases"() {
        setup:
        def card = new Card("SHED_T", 0, 0, FrequencyType.RECURRING, [])
        def k0 = kingdom(population: 100, production: 500, food: 750, season: Season.SPRING, table: [card])
        def state = twoSeats(Phase.PLAY, Season.SPRING, k0, kingdom())
        def engine = new GameEngine(state, new Random())
        engine.setActiveSeat(0)
        engine.endAndRun()

        when:
        engine.demolishCard(0)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("already submitted")
    }

    def "ADD: the phase holds until both seats have picked or skipped, then every seat draws its PLAY hand"() {
        setup:
        def k0 = kingdom(population: 100, production: 500, food: 750)
        def k1 = kingdom(population: 100, production: 500, food: 750)
        def state = twoSeats(Phase.ADD, Season.SPRING, k0, k1)
        def engine = new GameEngine(state, new Random())
        def pick = new Card("PICK_T", 0, 0, FrequencyType.RECURRING, [])

        when:
        engine.setActiveSeat(0)
        engine.pickCard(pick)

        then: "seat 0 committed; clock waits for seat 1"
        with(engine.state) {
            time.phase == Phase.ADD
            getSubmittedSeats() == [0]
            getPlayer(0).hand.isEmpty()
        }

        when:
        engine.setActiveSeat(1)
        engine.skipAdd()

        then: "barrier releases: ADD -> PLAY once, and seat 0's hand was drawn from its pile"
        with(engine.state) {
            time.phase == Phase.PLAY
            getSubmittedSeats() == []
            getPlayer(0).getHand()*.name() == ["PICK_T"]
        }
    }

    // ----- Slice 2: bot driving -----

    def "a bot seat auto-submits each phase, so the human alone gates the barrier"() {
        setup:
        def human = new Player(UUID.randomUUID(), "human")
        def bot = new Player(UUID.randomUUID(), "bot")
        def state = GameState.newTwoPlayerState()
        def engine = new GameEngine.Builder()
                .withId(UUID.randomUUID())
                .withPlayer(human)
                .withPlayer(bot, new KhHeuristicStrategy())
                .withState(state)
                .withNotificationExecutor({ it.run() } as Executor)
                .build()

        when: "the game starts"
        engine.initialize()

        then: "the bot has already taken (and submitted) its ADD action"
        with(engine.state) {
            time.phase == Phase.ADD
            isSubmitted(1)
            !isSubmitted(0)
        }

        when: "the human completes ADD"
        engine.setActiveSeat(0)
        engine.pickCard(engine.state.getPlayer(0).addChoices[0])

        then: "the round advanced to PLAY and the bot has already submitted PLAY too"
        with(engine.state) {
            time.phase == Phase.PLAY
            isSubmitted(1)
            !isSubmitted(0)
        }

        when: "the human ends PLAY"
        engine.setActiveSeat(0)
        engine.endAndRun()

        then: "both tables resolved, but the clock is held for Continue and the bot has auto-continued"
        with(engine.state) {
            resolutionPending
            time.phase == Phase.PLAY
            time.season == Season.SPRING
            !getPlayer(0).resolutionLog.isEmpty()
            !getPlayer(1).resolutionLog.isEmpty()
            isSubmitted(1)
            !isSubmitted(0)
        }

        when: "the human clicks Continue on the resolution screen"
        engine.setActiveSeat(0)
        engine.continueResolution()

        then: "the clock advances to the next season's ADD and the bot has already taken its ADD turn"
        with(engine.state) {
            !resolutionPending
            time.phase == Phase.ADD
            time.season == Season.SUMMER
            isSubmitted(1)
            !isSubmitted(0)
        }
    }

    // ----- Slice 3: sabotage resolves against a pre-resolution snapshot -----

    def "a sabotage card drains the opponent only after all self-resolution, against the frozen snapshot"() {
        setup: "seat 0 fields SABOTEURS; seat 1 has its own production card"
        def income = new Card("INCOME_T", 0, 0, FrequencyType.RECURRING, [ResourceTarget.PRODUCTION.integer(300)])
        def k0 = kingdom(population: 100, production: 500, food: 750, strength: 100, strengthUnlocked: true,
                season: Season.SUMMER, table: [CardCatalog.SABOTEURS])
        def k1 = kingdom(population: 100, production: 500, food: 750, season: Season.SUMMER, table: [income])
        def state = twoSeats(Phase.PLAY, Season.SUMMER, k0, k1)
        def engine = new GameEngine(state, new Random())

        when: "only the attacker has submitted"
        engine.setActiveSeat(0)
        engine.endAndRun()

        then: "nothing has been drained yet — resolution waits on the barrier"
        engine.state.getPlayer(1).resources.production() == 500.0d

        when:
        engine.setActiveSeat(1)
        engine.endAndRun()

        then: "the victim's own card fired first, then the flat -200 drain applied"
        with(engine.state) {
            getPlayer(1).resources.production() == 600.0d    // 500 + 300 (own card) - 200 (sabotage)
            getPlayer(0).resources.strength() == 75.0d       // the attacker paid its own strength cost
            getPlayer(1).resolutionLog.last().cardName() == "SABOTAGE"
            getPlayer(1).resolutionLog.last().productionDelta() == -200.0d
        }
    }

    // ----- Slice 4: forfeit and match outcome -----

    def "forfeit knocks the seat out, decides the match, and fires gameOver"() {
        setup:
        def observer = Mock(KhObserver)
        def lifecycle = Mock(GameLifecycleObserver)
        def state = twoSeats(Phase.PLAY, Season.SPRING, kingdom(), kingdom())
        def engine = new GameEngine.Builder()
                .withId(UUID.randomUUID())
                .withPlayer(new Player(UUID.randomUUID(), "a"))
                .withPlayer(new Player(UUID.randomUUID(), "b"))
                .withState(state)
                .addListener(observer)
                .addLifecycleObserver(lifecycle)
                .withNotificationExecutor({ it.run() } as Executor)
                .build()

        when:
        engine.forfeit(1)

        then:
        with(engine.state) {
            getPlayer(1).forfeited
            isPlayerOut(1)
            isMatchOver()
            winnerSeat() == 0
        }
        1 * observer.gameOver(engine, _)
        1 * lifecycle.gameOver(engine)
    }

    def "forfeiting an invalid seat is rejected; a decided match ignores further forfeits"() {
        setup:
        def state = twoSeats(Phase.PLAY, Season.SPRING, kingdom(), kingdom())
        def engine = new GameEngine(state, new Random())

        when:
        engine.forfeit(2)

        then:
        thrown(IllegalArgumentException)

        when:
        engine.forfeit(1)
        engine.forfeit(0)

        then: "the second forfeit is a no-op — seat 0 already won"
        !engine.state.getPlayer(0).forfeited
        engine.state.winnerSeat() == 0
    }

    def "victory now fires gameOver (single seat): completing the last Great Work ends the match"() {
        setup:
        def observer = Mock(KhObserver)
        def almostDone = CardCatalog.THE_GREAT_CONQUEST_CONSTRUCTION.withConstructionPeriod(1)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.WINTER)
                .population(1500.0).production(5000.0).food(5000.0).gold(5000.0).strength(5000.0)
                .goldUnlocked(true).strengthUnlocked(true)
                .table(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_COMPLETED])
                .table(Season.SUMMER, [CardCatalog.THE_GREAT_ROAD_COMPLETED])
                .table(Season.FALL, [CardCatalog.THE_GREAT_CAPITAL_COMPLETED])
                .table(Season.WINTER, [almostDone])
                .build()
        def engine = new GameEngine.Builder()
                .withId(UUID.randomUUID())
                .withPlayer(new Player(UUID.randomUUID(), "solo"))
                .withState(state)
                .addListener(observer)
                .withNotificationExecutor({ it.run() } as Executor)
                .build()

        when:
        engine.endAndRun()

        then:
        with(engine.state) {
            getPlayer(0).gameWon
            isMatchOver()
            winnerSeat() == 0
            time.phase == Phase.PLAY   // the clock stopped with the match
        }
        1 * observer.gameOver(engine, _)
    }
}
