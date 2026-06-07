package com.codenzasoft.games.hearts

import com.codenzasoft.games.common.Player
import com.codenzasoft.games.hearts.strategies.DefensiveStrategy
import com.codenzasoft.games.hearts.strategies.RandomStrategy
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Executor
import java.util.concurrent.Executors

class HeartsFullGameSpec extends Specification {

    def p1 = new Player(UUID.randomUUID(), "alice")
    def p2 = new Player(UUID.randomUUID(), "bob")
    def p3 = new Player(UUID.randomUUID(), "carol")
    def p4 = new Player(UUID.randomUUID(), "dave")

    def buildGame(HeartsListener listener, HeartsPlayerObserver strategy = new RandomStrategy()) {
        def builder = new HeartsGame.Builder()
                .withPlayers([p1, p2, p3, p4])
                .withId(UUID.randomUUID())
                .withNotificationExecutor(Executors.newSingleThreadExecutor())
                .addListener(listener)
        [p1, p2, p3, p4].each { builder.addPlayerObserver(it, strategy) }
        builder.build()
    }

    def "four bots complete a full game without errors using any strategy"() {
        def listener = Mock(HeartsListener)
        def conditions = new PollingConditions(timeout: 10, initialDelay: 0.5, delay: 0.5)

        when:
        def game = buildGame(listener, strategy)

        then:
        conditions.eventually {
            game.getGameState().isOver()
        }
        game.gameState.currentRound.get().hands().values().every { it.isEmpty() }

        where:
        strategy || _
        new RandomStrategy() || _
        new DefensiveStrategy() || _
    }

}
