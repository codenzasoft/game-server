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
    }

    def findNextPlayer(HeartsGame game) {
        def state = game.gameState
        def name = state.currentRound.get().getNextPlayer(state).get()
        [p1, p2, p3, p4].find { it.name() == name }
    }

    def "yourTurnToPass fires for each player when a non-KEEPER round starts"() {
        def observers = [p1, p2, p3, p4].collectEntries { [it, Mock(HeartsPlayerObserver)] }

        when:
        buildGame(observers)

        then:
        [p1, p2, p3, p4].each { p ->
            1 * observers[p].yourTurnToPass(_, _, p, _)
        }
    }

    def "yourTurnToPass provides the player's 13-card dealt hand"() {
        def observers = [p1, p2, p3, p4].collectEntries { [it, Mock(HeartsPlayerObserver)] }

        when:
        buildGame(observers)

        then:
        [p1, p2, p3, p4].each { p ->
            1 * observers[p].yourTurnToPass(_, _, p, { it.size() == 13 })
        }
    }

}
