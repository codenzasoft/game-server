package com.codenzasoft.games.kingshand

import com.codenzasoft.games.common.Player
import spock.lang.Specification

import java.util.concurrent.Executor

class GameEngineNotificationSpec extends Specification {

    private static final Player TEST_PLAYER = new Player(UUID.randomUUID(), "test")

    /** Builds an engine wired with the given listeners and a synchronous notification executor. */
    private static GameEngine engineWith(GameState state, KhObserver... listeners) {
        def builder = new GameEngine.Builder()
                .withId(UUID.randomUUID())
                .withPlayer(TEST_PLAYER)
                .withState(state)
                .withEventCatalog(new EventCatalog([:]))
                .withNotificationExecutor({ it.run() } as Executor)
        listeners.each { builder.addListener(it) }
        builder.build()
    }

    def "initialize fires gameStarted on a fresh game"() {
        setup:
        def listener = Mock(KhObserver)
        def state = GameState.newState()
        def engine = engineWith(state, listener)

        when:
        engine.initialize()

        then:
        1 * listener.gameStarted(engine, state)
    }

    def "initialize does not fire gameStarted when the game is already over"() {
        setup:
        def listener = Mock(KhObserver)
        def state = GameState.builder().population(0.0).gameOver(true).build()
        def engine = engineWith(state, listener)

        when:
        engine.initialize()

        then:
        0 * listener.gameStarted(_, _)
    }

    def "population dropping to zero via an event option fires gameOver"() {
        setup:
        def listener = Mock(KhObserver)
        def option = new EventOption("Plague", [ResourceTarget.POPULATION.integer(-5)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SPRING).year(1)
                .population(5.0).build()
        def engine = engineWith(state, listener)

        when:
        engine.applyEventOption(option)

        then:
        1 * listener.gameOver(engine, state)
    }

    def "gameOver does not fire again once the game is already over"() {
        setup:
        def listener = Mock(KhObserver)
        def option = new EventOption("Aftermath", [ResourceTarget.POPULATION.integer(-1)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SPRING).year(1)
                .population(0.0).gameOver(true).build()
        def engine = engineWith(state, listener)

        when:
        engine.applyEventOption(option)

        then:
        0 * listener.gameOver(_, _)
    }

    def "an event that leaves population above zero does not fire gameOver"() {
        setup:
        def listener = Mock(KhObserver)
        def option = new EventOption("Minor loss", [ResourceTarget.POPULATION.integer(-2)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SPRING).year(1)
                .population(5.0).build()
        def engine = engineWith(state, listener)

        when:
        engine.applyEventOption(option)

        then:
        0 * listener.gameOver(_, _)
    }

    def "all registered listeners receive gameStarted"() {
        setup:
        def listener1 = Mock(KhObserver)
        def listener2 = Mock(KhObserver)
        def state = GameState.newState()
        def engine = engineWith(state, listener1, listener2)

        when:
        engine.initialize()

        then:
        1 * listener1.gameStarted(engine, state)
        1 * listener2.gameStarted(engine, state)
    }

    def "all registered listeners receive gameOver"() {
        setup:
        def listener1 = Mock(KhObserver)
        def listener2 = Mock(KhObserver)
        def option = new EventOption("Plague", [ResourceTarget.POPULATION.integer(-5)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SPRING).year(1)
                .population(5.0).build()
        def engine = engineWith(state, listener1, listener2)

        when:
        engine.applyEventOption(option)

        then:
        1 * listener1.gameOver(engine, state)
        1 * listener2.gameOver(engine, state)
    }
}
