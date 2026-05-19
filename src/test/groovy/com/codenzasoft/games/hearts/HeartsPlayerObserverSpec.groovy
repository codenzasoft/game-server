package com.codenzasoft.games.hearts

import com.codenzasoft.games.common.Player
import spock.lang.Specification

import java.util.concurrent.Executor

class HeartsPlayerObserverSpec extends Specification {

    def p1 = new Player(UUID.randomUUID(), "alice")
    def p2 = new Player(UUID.randomUUID(), "bob")
    def p3 = new Player(UUID.randomUUID(), "carol")
    def p4 = new Player(UUID.randomUUID(), "dave")

    def buildGame(Map<Player, HeartsPlayerObserver> observers = [:]) {
        def builder = new HeartsGame.Builder()
                .withPlayers([p1, p2, p3, p4])
                .withId(UUID.randomUUID())
                .withNotificationExecutor({ it.run() } as Executor)
        observers.each { player, observer -> builder.addPlayerObserver(player, observer) }
        builder.build()
    }

    def selectPassesAndPass(HeartsGame game) {
        if (game.gameState.currentRound.get().pass() != Pass.KEEPER) {
            [p1, p2, p3, p4].each { player ->
                def hand = game.gameState.currentRound.get().hands().get(player.name())
                game.setPass(player, hand.take(3))
            }
        }
        game.passCards()
    }

    def findNextPlayer(HeartsGame game) {
        def state = game.gameState
        def name = state.currentRound.get().getNextPlayer(state).get()
        [p1, p2, p3, p4].find { it.name() == name }
    }

    def "yourTurnToPass fires for each player when a non-KEEPER round starts"() {
        def observers = [p1, p2, p3, p4].collectEntries { [it, Mock(HeartsPlayerObserver)] }
        def game = buildGame(observers)

        when:
        game.startNewRound()

        then:
        [p1, p2, p3, p4].each { p ->
            1 * observers[p].yourTurnToPass(game, _, p, _)
        }
    }

    def "yourTurnToPass provides the player's 13-card dealt hand"() {
        def observers = [p1, p2, p3, p4].collectEntries { [it, Mock(HeartsPlayerObserver)] }
        def game = buildGame(observers)

        when:
        game.startNewRound()

        then:
        [p1, p2, p3, p4].each { p ->
            1 * observers[p].yourTurnToPass(game, _, p, { it.size() == 13 })
        }
    }

    def "yourTurnToPlay fires exactly once for each player during a complete trick"() {
        def observers = [p1, p2, p3, p4].collectEntries { [it, Mock(HeartsPlayerObserver)] }
        def game = buildGame(observers)
        game.startNewRound()
        selectPassesAndPass(game)

        when:
        game.startNewTrick()
        4.times {
            def player = findNextPlayer(game)
            game.playCard(player, game.getValidCards(player).first())
        }

        then:
        [p1, p2, p3, p4].each { p ->
            1 * observers[p].yourTurnToPlay(game, _, p, _)
        }
    }

    def "yourTurnToPlay for the trick leader passes only the 2 of clubs as valid cards"() {
        def observers = [p1, p2, p3, p4].collectEntries { [it, Mock(HeartsPlayerObserver)] }
        def game = buildGame(observers)
        game.startNewRound()
        selectPassesAndPass(game)
        // Find the leader by who holds the 2♣ (trick 1 leader), before starting the trick
        def leader = [p1, p2, p3, p4].find { p ->
            game.gameState.currentRound.get().hands().get(p.name()).contains(HeartsGame.TWO_OF_CLUBS)
        }

        when:
        game.startNewTrick()

        then:
        1 * observers[leader].yourTurnToPlay(game, _, leader, { it == [HeartsGame.TWO_OF_CLUBS] as Set })
    }

    def "players without a registered observer cause no errors"() {
        // Only p1 has an observer; p2, p3, p4 do not
        def p1Observer = Mock(HeartsPlayerObserver)
        def game = buildGame([(p1): p1Observer])
        game.startNewRound()
        selectPassesAndPass(game)

        when:
        game.startNewTrick()
        4.times {
            def player = findNextPlayer(game)
            game.playCard(player, game.getValidCards(player).first())
        }

        then:
        noExceptionThrown()
    }
}
