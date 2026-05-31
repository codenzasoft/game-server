package com.codenzasoft.games.hearts

import com.codenzasoft.games.common.Player
import com.codenzasoft.games.hearts.strategies.RandomStrategy
import spock.lang.Specification

import java.util.concurrent.Executor

class HeartsFullGameSpec extends Specification {

    def p1 = new Player(UUID.randomUUID(), "alice")
    def p2 = new Player(UUID.randomUUID(), "bob")
    def p3 = new Player(UUID.randomUUID(), "carol")
    def p4 = new Player(UUID.randomUUID(), "dave")

    /**
     * Listener that drives the game forward automatically:
     * - triggers passCards once all four passes are selected (or immediately for KEEPER)
     * - starts a new trick after the pass completes
     * - starts the next trick after each trick ends (until the round is complete)
     */
    static HeartsListener gameController(Closure onRoundEnded) {
        [
            roundStarted: { HeartsGame game, HeartsState state ->
                if (state.currentRound.get().pass() == Pass.KEEPER) {
                    game.passCards()
                }
            },
            passSelected: { HeartsGame game, HeartsState state, Player player, List cards ->
                if (state.currentRound.get().passReady) {
                    game.passCards()
                }
            },
            passComplete: { HeartsGame game, HeartsState state ->
                game.startNewTrick()
            },
            trickEnded: { HeartsGame game, HeartsState state ->
                if (!state.currentRound.get().isComplete()) {
                    game.startNewTrick()
                }
            },
            roundEnded: { HeartsGame game, HeartsState state ->
                onRoundEnded(game, state)
            }
        ] as HeartsListener
    }

    def buildGame(HeartsListener controller) {
        def strategy = new RandomStrategy()
        def builder = new HeartsGame.Builder()
                .withPlayers([p1, p2, p3, p4])
                .withId(UUID.randomUUID())
                .withNotificationExecutor({ it.run() } as Executor)
                .addListener(controller)
        [p1, p2, p3, p4].each { builder.addPlayerObserver(it, strategy) }
        builder.build()
    }

    def "four random-strategy bots complete a full round without errors"() {
        def roundsEnded = 0
        def game = buildGame(gameController { g, s -> roundsEnded++ })

        when:
        game.startNewRound()

        then:
        roundsEnded == 1
        game.gameState.currentRound.get().hands().values().every { it.isEmpty() }
    }

    def "four random-strategy bots play four rounds covering all pass directions"() {
        def roundsEnded = 0
        def game = buildGame(gameController { g, s ->
            roundsEnded++
            if (roundsEnded < 4) g.startNewRound()
        })

        when:
        game.startNewRound()

        then:
        roundsEnded == 4
        game.gameState.currentRound.get().hands().values().every { it.isEmpty() }
    }
}
