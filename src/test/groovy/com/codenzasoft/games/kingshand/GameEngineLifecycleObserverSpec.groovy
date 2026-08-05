package com.codenzasoft.games.kingshand

import com.codenzasoft.games.common.GameLifecycleObserver
import com.codenzasoft.games.common.Player
import spock.lang.Specification

import java.util.concurrent.Executor

/**
 * Verifies KH's {@link GameEngine} notifies the common {@link GameLifecycleObserver} (GAME-116)
 * alongside its existing KH-specific {@link KhObserver} notifications. Mirrors the structure of
 * {@code GameEngineNotificationSpec}.
 */
class GameEngineLifecycleObserverSpec extends Specification {

    private static final Player TEST_PLAYER = new Player(UUID.randomUUID(), "test")

    /** Builds an engine wired with the given common lifecycle observers and a synchronous executor. */
    private static GameEngine engineWith(GameState state, GameLifecycleObserver<GameEngine>... observers) {
        def builder = new GameEngine.Builder()
                .withId(UUID.randomUUID())
                .withPlayer(TEST_PLAYER)
                .withState(state)
                .withNotificationExecutor({ it.run() } as Executor)
        observers.each { builder.addLifecycleObserver(it) }
        builder.build()
    }

    def "initialize fires gameStarted on a fresh game"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def state = GameState.newState()
        def engine = engineWith(state, observer)

        when:
        engine.initialize()

        then:
        1 * observer.gameStarted(engine)
    }

    def "initialize does not fire gameStarted when the game is already over"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def state = GameState.builder().population(0.0).build()
        def engine = engineWith(state, observer)

        when:
        engine.initialize()

        then:
        0 * observer.gameStarted(_)
    }

    def "initialize never fires gameResumed"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def state = GameState.newState()
        def engine = engineWith(state, observer)

        when:
        engine.initialize()

        then:
        0 * observer.gameResumed(_)
    }

    def "population dropping to zero during end-of-season resolution fires gameOver"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def plague = new Card("Plague", 0, 0, FrequencyType.SINGLE, [ResourceTarget.POPULATION.integer(-5)])
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING).year(1)
                .table(Season.SPRING, [plague])
                .population(5.0).build()
        def engine = engineWith(state, observer)

        when:
        engine.endAndRun()

        then:
        1 * observer.gameOver(engine)
    }

    def "gameOver does not fire again once the game is already over"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def aftermath = new Card("Aftermath", 0, 0, FrequencyType.SINGLE, [ResourceTarget.POPULATION.integer(-1)])
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING).year(1)
                .table(Season.SPRING, [aftermath])
                .population(0.0).build()
        def engine = engineWith(state, observer)

        when:
        engine.endAndRun()

        then:
        0 * observer.gameOver(_)
    }

    def "quit fires gameQuit and marks the game over"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def state = GameState.newState()
        def engine = engineWith(state, observer)

        when:
        engine.quit()

        then:
        1 * observer.gameQuit(engine)

    }

    def "quit does not fire again once the game is already over"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def state = GameState.builder().population(0.0).build()
        def engine = engineWith(state, observer)

        when:
        engine.quit()

        then:
        0 * observer.gameQuit(_)
    }

    def "quit does not fire gameOver"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def state = GameState.newState()
        def engine = engineWith(state, observer)

        when:
        engine.quit()

        then:
        0 * observer.gameOver(_)
    }

    def "all registered observers receive gameStarted"() {
        setup:
        def observer1 = Mock(GameLifecycleObserver)
        def observer2 = Mock(GameLifecycleObserver)
        def state = GameState.newState()
        def engine = engineWith(state, observer1, observer2)

        when:
        engine.initialize()

        then:
        1 * observer1.gameStarted(engine)
        1 * observer2.gameStarted(engine)
    }

    def "all registered observers receive gameQuit"() {
        setup:
        def observer1 = Mock(GameLifecycleObserver)
        def observer2 = Mock(GameLifecycleObserver)
        def state = GameState.newState()
        def engine = engineWith(state, observer1, observer2)

        when:
        engine.quit()

        then:
        1 * observer1.gameQuit(engine)
        1 * observer2.gameQuit(engine)
    }

    def "legacy KhObserver and the common GameLifecycleObserver both receive notifications"() {
        setup:
        def khObserver = Mock(KhObserver)
        def lifecycleObserver = Mock(GameLifecycleObserver)
        def state = GameState.newState()
        def builder = new GameEngine.Builder()
                .withId(UUID.randomUUID())
                .withPlayer(TEST_PLAYER)
                .withState(state)
                .withNotificationExecutor({ it.run() } as Executor)
                .addListener(khObserver)
                .addLifecycleObserver(lifecycleObserver)
        def engine = builder.build()

        when:
        engine.initialize()

        then:
        1 * khObserver.gameStarted(engine, _)
        1 * lifecycleObserver.gameStarted(engine)
    }
}
