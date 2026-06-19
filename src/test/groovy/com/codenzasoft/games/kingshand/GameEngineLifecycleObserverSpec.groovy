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

    /** Builds a fresh (non-restored) engine wired with the given common lifecycle observers and a synchronous executor. */
    private static GameEngine engineWith(GameState state, GameLifecycleObserver<GameEngine>... observers) {
        def builder = new GameEngine.Builder()
                .withId(UUID.randomUUID())
                .withPlayer(TEST_PLAYER)
                .withState(state)
                .withEventCatalog(new EventCatalog([:]))
                .withNotificationExecutor({ it.run() } as Executor)
        observers.each { builder.addLifecycleObserver(it) }
        builder.build()
    }

    /** Builds an engine restored from a persisted state, wired with the given common lifecycle observers and a synchronous executor. */
    private static GameEngine restoredEngineWith(GameState state, GameLifecycleObserver<GameEngine>... observers) {
        def builder = new GameEngine.Builder()
                .withId(UUID.randomUUID())
                .withPlayer(TEST_PLAYER)
                .withRestoredState(state)
                .withEventCatalog(new EventCatalog([:]))
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
        def state = GameState.builder().population(0.0).gameOver(true).build()
        def engine = engineWith(state, observer)

        when:
        engine.initialize()

        then:
        0 * observer.gameStarted(_)
    }

    def "initialize never fires gameResumed for a fresh game"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def state = GameState.newState()
        def engine = engineWith(state, observer)

        when:
        engine.initialize()

        then:
        0 * observer.gameResumed(_)
    }

    def "initialize fires gameResumed, not gameStarted, when built with a restored state"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def state = GameState.newState()
        def engine = restoredEngineWith(state, observer)

        when:
        engine.initialize()

        then:
        1 * observer.gameResumed(engine)
        0 * observer.gameStarted(_)
    }

    def "initialize on a restored engine does not reseed the starting deck or re-offer ADD choices"() {
        setup:
        // A snapshot saved mid-game: draw piles already seeded, an ADD choice already offered.
        def offeredChoice = CardCatalog.defaultCatalog().forSeason(Season.SPRING).first()
        def state = GameState.builder()
                .curPhase(Phase.ADD)
                .addChoices([offeredChoice])
                .build()
        def engine = restoredEngineWith(state)

        when:
        engine.initialize()

        then:
        // Untouched: seedStartingDeck() would have populated the draw piles; it must not run again.
        Season.values().every { engine.state.getDrawPile().get(it).isEmpty() }
        // Untouched: offerAddChoices() would have replaced this with a new random offer.
        engine.state.getAddChoices() == [offeredChoice]
    }

    def "initialize on a restored engine does not notify the legacy KhObserver"() {
        setup:
        def khObserver = Mock(KhObserver)
        def state = GameState.newState()
        def builder = new GameEngine.Builder()
                .withId(UUID.randomUUID())
                .withPlayer(TEST_PLAYER)
                .withRestoredState(state)
                .withEventCatalog(new EventCatalog([:]))
                .withNotificationExecutor({ it.run() } as Executor)
                .addListener(khObserver)
        def engine = builder.build()

        when:
        engine.initialize()

        then:
        0 * khObserver.gameStarted(_, _)
    }

    def "population dropping to zero via an event option fires gameOver"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def option = new EventOption("Plague", [ResourceTarget.POPULATION.integer(-5)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SPRING).year(1)
                .population(5.0).build()
        def engine = engineWith(state, observer)

        when:
        engine.applyEventOption(option)

        then:
        1 * observer.gameOver(engine)
    }

    def "gameOver does not fire again once the game is already over"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def option = new EventOption("Aftermath", [ResourceTarget.POPULATION.integer(-1)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SPRING).year(1)
                .population(0.0).gameOver(true).build()
        def engine = engineWith(state, observer)

        when:
        engine.applyEventOption(option)

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
        engine.state.isGameOver()
    }

    def "quit does not fire again once the game is already over"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def state = GameState.builder().gameOver(true).build()
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
                .withEventCatalog(new EventCatalog([:]))
                .withNotificationExecutor({ it.run() } as Executor)
                .addListener(khObserver)
                .addLifecycleObserver(lifecycleObserver)
        def engine = builder.build()

        when:
        engine.initialize()

        then:
        1 * khObserver.gameStarted(engine, state)
        1 * lifecycleObserver.gameStarted(engine)
    }
}
