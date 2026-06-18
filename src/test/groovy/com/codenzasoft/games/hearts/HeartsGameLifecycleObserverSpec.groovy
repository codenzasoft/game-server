package com.codenzasoft.games.hearts

import com.codenzasoft.games.common.GameLifecycleObserver
import com.codenzasoft.games.common.Player
import com.codenzasoft.games.common.cards.Card
import com.codenzasoft.games.common.cards.Rank
import com.codenzasoft.games.common.cards.Suit
import spock.lang.Specification

import java.util.concurrent.Executor

/**
 * Verifies Hearts' {@link HeartsGame} notifies the common {@link GameLifecycleObserver} (GAME-117)
 * alongside its existing Hearts-specific {@link HeartsListener} notifications. Mirrors the structure
 * of {@code GameEngineLifecycleObserverSpec} (KH, GAME-116).
 */
class HeartsGameLifecycleObserverSpec extends Specification {

    private static List<Player> newPlayers() {
        HeartsStateBuilder.PLAYERS.collect { new Player(UUID.randomUUID(), it) }
    }

    /** Builds a fresh Hearts game (no restored state) wired with a synchronous executor. */
    private static HeartsGame freshGameWith(List<Player> players, GameLifecycleObserver<HeartsGame>... observers) {
        def builder = new HeartsGame.Builder()
                .withId(UUID.randomUUID())
                .withPlayers(players)
                .withNotificationExecutor({ it.run() } as Executor)
        observers.each { builder.addLifecycleObserver(it) }
        builder.build()
    }

    /** Builds a Hearts game restored from the given state, wired with a synchronous executor. */
    private static HeartsGame restoredGameWith(List<Player> players, HeartsState state, GameLifecycleObserver<HeartsGame>... observers) {
        def builder = new HeartsGame.Builder()
                .withId(UUID.randomUUID())
                .withPlayers(players)
                .withRestoredState(state)
                .withNotificationExecutor({ it.run() } as Executor)
        observers.each { builder.addLifecycleObserver(it) }
        builder.build()
    }

    /** A completed round in which {@code winner} takes the queen of spades plus three low hearts (16 points). */
    private static RoundState fullRoundWonBy(int number, String winner, List<String> others) {
        def trick = new TrickState(1, winner, [
                new PlayedCard(winner, HeartsGame.QUEEN_OF_SPADES),
                new PlayedCard(others[0], new Card(Suit.HEARTS, Rank.TWO)),
                new PlayedCard(others[1], new Card(Suit.HEARTS, Rank.THREE)),
                new PlayedCard(others[2], new Card(Suit.HEARTS, Rank.FOUR)),
        ])
        new RoundState(number, Pass.KEEPER, winner, [:], [:], true, [trick], [:])
    }

    def "construction fires gameStarted for a fresh game"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def players = newPlayers()

        when:
        freshGameWith(players, observer)

        then:
        1 * observer.gameStarted(_)
    }

    def "construction never fires gameResumed for a fresh game"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def players = newPlayers()

        when:
        freshGameWith(players, observer)

        then:
        0 * observer.gameResumed(_)
    }

    def "construction fires gameResumed, not gameStarted, when restored from persisted state"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def players = newPlayers()
        def state = HeartsStateBuilder.state(players: players*.name())

        when:
        restoredGameWith(players, state, observer)

        then:
        1 * observer.gameResumed(_)
        0 * observer.gameStarted(_)
    }

    def "playing the final card of the final trick that pushes a player's score to 100 fires gameOver"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def players = newPlayers()
        def alice = players.find { it.name() == "alice" }

        // Six complete rounds worth 16 points each to alice (96 total) - not yet over.
        def priorRounds = (1..6).collect { fullRoundWonBy(it, "alice", ["bob", "carol", "dave"]) }

        // A seventh round, one card away from completion: bob/carol/dave have already played low
        // spades in the final trick; alice holds only the queen of spades. Playing it wins the trick
        // (and the round), pushing alice's cumulative score from 96 to 109.
        def liveTrick = new TrickState(1, "bob", [
                new PlayedCard("bob", new Card(Suit.SPADES, Rank.TWO)),
                new PlayedCard("carol", new Card(Suit.SPADES, Rank.THREE)),
                new PlayedCard("dave", new Card(Suit.SPADES, Rank.FOUR)),
        ])
        def liveRound = new RoundState(7, Pass.KEEPER, "alice", [:], [:], true, [liveTrick],
                ["alice": [HeartsGame.QUEEN_OF_SPADES]])

        def state = new HeartsState(players*.name(), priorRounds + [liveRound], false, null)
        def game = restoredGameWith(players, state, observer)

        when:
        game.playCard(alice, HeartsGame.QUEEN_OF_SPADES)

        then:
        1 * observer.gameOver(game)
        game.getGameState().isOver()
    }

    def "gameOver does not fire when a trick completes without ending the game"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def players = newPlayers()
        def alice = players.find { it.name() == "alice" }

        def liveTrick = new TrickState(1, "bob", [
                new PlayedCard("bob", new Card(Suit.SPADES, Rank.TWO)),
                new PlayedCard("carol", new Card(Suit.SPADES, Rank.THREE)),
                new PlayedCard("dave", new Card(Suit.SPADES, Rank.FOUR)),
        ])
        def liveRound = new RoundState(1, Pass.KEEPER, "alice", [:], [:], true, [liveTrick],
                ["alice": [HeartsGame.QUEEN_OF_SPADES]])
        def state = new HeartsState(players*.name(), [liveRound], false, null)
        def game = restoredGameWith(players, state, observer)

        when:
        game.playCard(alice, HeartsGame.QUEEN_OF_SPADES)

        then:
        0 * observer.gameOver(_)
        !game.getGameState().isOver()
    }

    def "quit fires gameQuit and not gameOver"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def players = newPlayers()

        when:
        def game = freshGameWith(players, observer)
        game.quit("alice")

        then:
        1 * observer.gameQuit(game)
        0 * observer.gameOver(_)
    }

    def "all registered observers receive gameStarted"() {
        setup:
        def observer1 = Mock(GameLifecycleObserver)
        def observer2 = Mock(GameLifecycleObserver)
        def players = newPlayers()

        when:
        freshGameWith(players, observer1, observer2)

        then:
        1 * observer1.gameStarted(_)
        1 * observer2.gameStarted(_)
    }

    def "legacy HeartsListener and the common GameLifecycleObserver both receive notifications"() {
        setup:
        def heartsListener = Mock(HeartsListener)
        def lifecycleObserver = Mock(GameLifecycleObserver)
        def players = newPlayers()
        def builder = new HeartsGame.Builder()
                .withId(UUID.randomUUID())
                .withPlayers(players)
                .withNotificationExecutor({ it.run() } as Executor)
                .addListener(heartsListener)
                .addLifecycleObserver(lifecycleObserver)

        when:
        def game = builder.build()

        then:
        1 * heartsListener.gameStarted(game, _)
        1 * lifecycleObserver.gameStarted(_)
    }
}
