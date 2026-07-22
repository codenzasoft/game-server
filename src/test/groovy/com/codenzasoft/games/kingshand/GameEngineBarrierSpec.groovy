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
 * <p>Single-seat parity is covered by the existing {@link GameEngineSpec} suite, which must stay
 * green unchanged: with one seat the barrier releases immediately.
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
        state.markSubmitted(0)

        then:
        state.isSubmitted(0)
        !state.allSubmitted()
        state.submittedSeats == [0]

        when:
        state.getPlayer(1).forfeit()

        then: "a forfeited seat no longer blocks the barrier"
        state.allSubmitted()

        when:
        state.clearSubmitted()

        then:
        !state.isSubmitted(0)
        state.submittedSeats == []
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
        state.time.phase == Phase.PLAY
        state.time.season == Season.SPRING
        state.submittedSeats == [0]
        k0.resources.production() == 500.0d
        k0.resolutionLog.isEmpty()
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

        then: "both kingdoms resolved"
        k0.resources.production() == 600.0d
        k1.resources.production() == 540.0d
        !k0.resolutionLog.isEmpty()
        !k1.resolutionLog.isEmpty()

        and: "but the shared clock is held on the resolved season pending Continue"
        state.resolutionPending
        state.time.phase == Phase.PLAY
        state.time.season == Season.SPRING
        state.submittedSeats == []

        when: "both seats acknowledge the resolution screen"
        engine.setActiveSeat(0)
        engine.continueResolution()
        engine.setActiveSeat(1)
        engine.continueResolution()

        then: "now the shared clock advances exactly once (spring PLAY -> summer ADD) and seeds ADD"
        !state.resolutionPending
        state.time.phase == Phase.ADD
        state.time.season == Season.SUMMER
        state.time.year == 1
        state.submittedSeats == []
        !k0.addChoices.isEmpty()
        !k1.addChoices.isEmpty()
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
        state.time.phase == Phase.ADD
        state.submittedSeats == [0]
        k0.hand.isEmpty()

        when:
        engine.setActiveSeat(1)
        engine.skipAdd()

        then: "barrier releases: ADD -> PLAY once, and seat 0's hand was drawn from its pile"
        state.time.phase == Phase.PLAY
        state.submittedSeats == []
        k0.hand*.name() == ["PICK_T"]
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
        state.time.phase == Phase.ADD
        state.isSubmitted(1)
        !state.isSubmitted(0)

        when: "the human completes ADD"
        engine.setActiveSeat(0)
        engine.pickCard(state.getPlayer(0).addChoices[0])

        then: "the round advanced to PLAY and the bot has already submitted PLAY too"
        state.time.phase == Phase.PLAY
        state.isSubmitted(1)
        !state.isSubmitted(0)

        when: "the human ends PLAY"
        engine.setActiveSeat(0)
        engine.endAndRun()

        then: "both tables resolved, but the clock is held for Continue and the bot has auto-continued"
        state.resolutionPending
        state.time.phase == Phase.PLAY
        state.time.season == Season.SPRING
        !state.getPlayer(0).resolutionLog.isEmpty()
        !state.getPlayer(1).resolutionLog.isEmpty()
        state.isSubmitted(1)
        !state.isSubmitted(0)

        when: "the human clicks Continue on the resolution screen"
        engine.setActiveSeat(0)
        engine.continueResolution()

        then: "the clock advances to the next season's ADD and the bot has already taken its ADD turn"
        !state.resolutionPending
        state.time.phase == Phase.ADD
        state.time.season == Season.SUMMER
        state.isSubmitted(1)
        !state.isSubmitted(0)
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
        k1.resources.production() == 500.0d

        when:
        engine.setActiveSeat(1)
        engine.endAndRun()

        then: "the victim's own card fired first, then the flat -200 drain applied"
        k1.resources.production() == 600.0d    // 500 + 300 (own card) - 200 (sabotage)

        and: "the attacker paid its own strength cost"
        k0.resources.strength() == 75.0d

        and: "the victim's log ends with the sabotage step for the client animation"
        k1.resolutionLog.last().cardName() == "SABOTAGE"
        k1.resolutionLog.last().productionDelta() == -200.0d
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
        state.getPlayer(1).forfeited
        state.isPlayerOut(1)
        state.isMatchOver()
        state.winnerSeat() == 0
        1 * observer.gameOver(engine, state)
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
        !state.getPlayer(0).forfeited
        state.winnerSeat() == 0
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
        state.getPlayer(0).gameWon
        state.isMatchOver()
        state.winnerSeat() == 0
        1 * observer.gameOver(engine, state)

        and: "the clock stopped with the match"
        state.time.phase == Phase.PLAY
    }
}
