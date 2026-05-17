package com.codenzasoft.games.hearts

import com.codenzasoft.games.common.Player
import spock.lang.Specification

import java.util.concurrent.Executor

class HeartsGameNotificationSpec extends Specification {

    def p1 = new Player(UUID.randomUUID(), "alice")
    def p2 = new Player(UUID.randomUUID(), "bob")
    def p3 = new Player(UUID.randomUUID(), "carol")
    def p4 = new Player(UUID.randomUUID(), "dave")

    def buildGame(HeartsListener... listeners) {
        def builder = new HeartsGame.Builder()
                .withPlayers([p1, p2, p3, p4])
                .withId(UUID.randomUUID())
                .withNotificationExecutor({ it.run() } as Executor)
        listeners.each { builder.addListener(it) }
        builder.build()
    }

    /** Selects 3 cards for each player and executes the pass, handling KEEPER rounds. */
    def selectPassesAndPass(HeartsGame game) {
        def round = game.gameState.currentRound.get()
        if (round.pass() != Pass.KEEPER) {
            [p1, p2, p3, p4].each { player ->
                def hand = game.gameState.currentRound.get().hands().get(player.name())
                game.setPass(player, hand.take(3))
            }
        }
        game.passCards()
    }

    /** Plays one card for the current next player using the first card in their hand. */
    def playNextCard(HeartsGame game) {
        def state = game.gameState
        def round = state.currentRound.get()
        def nextPlayerName = round.getNextPlayer(state).get()
        def player = [p1, p2, p3, p4].find { it.name() == nextPlayerName }
        game.playCard(player, round.hands().get(nextPlayerName).first())
    }

    /** Plays all four cards in the current trick. */
    def playTrick(HeartsGame game) {
        4.times { playNextCard(game) }
    }

    def "gameStarted is fired on construction"() {
        def listener = Mock(HeartsListener)

        when:
        buildGame(listener)

        then:
        1 * listener.gameStarted(_, _)
    }

    def "gameOver fires gameOver notification"() {
        def listener = Mock(HeartsListener)
        def game = buildGame(listener)

        when:
        game.gameOver()

        then:
        1 * listener.gameOver(game, _)
    }

    def "quit fires gameQuit notification"() {
        def listener = Mock(HeartsListener)
        def game = buildGame(listener)

        when:
        game.quit()

        then:
        1 * listener.gameQuit(game, _)
    }

    def "startNewRound fires roundStarted with the new round"() {
        def listener = Mock(HeartsListener)
        def game = buildGame(listener)

        when:
        game.startNewRound()

        then:
        1 * listener.roundStarted(game, _, { it.number() == 1 })
    }

    def "setPass fires passSelected with the player and selected cards"() {
        def listener = Mock(HeartsListener)
        def game = buildGame(listener)
        game.startNewRound()
        def cards = game.gameState.currentRound.get().hands().get(p1.name()).take(3)

        when:
        game.setPass(p1, cards)

        then:
        1 * listener.passSelected(game, _, _, p1, cards)
    }

    def "passCards fires passComplete"() {
        def listener = Mock(HeartsListener)
        def game = buildGame(listener)
        game.startNewRound()
        def round = game.gameState.currentRound.get()
        if (round.pass() != Pass.KEEPER) {
            [p1, p2, p3, p4].each { player ->
                def hand = game.gameState.currentRound.get().hands().get(player.name())
                game.setPass(player, hand.take(3))
            }
        }

        when:
        game.passCards()

        then:
        1 * listener.passComplete(game, _, _)
    }

    def "startNewTrick fires trickStarted"() {
        def listener = Mock(HeartsListener)
        def game = buildGame(listener)
        game.startNewRound()
        selectPassesAndPass(game)

        when:
        game.startNewTrick()

        then:
        1 * listener.trickStarted(game, _, _, { it.number() == 1 })
    }

    def "playCard fires cardPlayed with sequential card numbers within the trick"() {
        def listener = Mock(HeartsListener)
        def game = buildGame(listener)
        game.startNewRound()
        selectPassesAndPass(game)
        game.startNewTrick()

        when:
        4.times { playNextCard(game) }

        then:
        1 * listener.cardPlayed(game, _, _, _, _, _, 1)
        1 * listener.cardPlayed(game, _, _, _, _, _, 2)
        1 * listener.cardPlayed(game, _, _, _, _, _, 3)
        1 * listener.cardPlayed(game, _, _, _, _, _, 4)
    }

    def "fourth card in a trick fires trickEnded but not roundEnded"() {
        def listener = Mock(HeartsListener)
        def game = buildGame(listener)
        game.startNewRound()
        selectPassesAndPass(game)
        game.startNewTrick()
        3.times { playNextCard(game) }

        when:
        playNextCard(game)

        then:
        1 * listener.trickEnded(game, _, _, _)
        0 * listener.roundEnded(_, _, _)
    }

    def "completing all tricks in a round fires roundEnded"() {
        def listener = Mock(HeartsListener)
        def game = buildGame(listener)
        game.startNewRound()
        selectPassesAndPass(game)

        when:
        13.times {
            game.startNewTrick()
            playTrick(game)
        }

        then:
        13 * listener.trickEnded(game, _, _, _)
        1 * listener.roundEnded(game, _, _)
    }

    def "all listeners receive each notification"() {
        def listener1 = Mock(HeartsListener)
        def listener2 = Mock(HeartsListener)
        def game = buildGame(listener1, listener2)

        when:
        game.startNewRound()

        then:
        1 * listener1.roundStarted(game, _, _)
        1 * listener2.roundStarted(game, _, _)
    }
}
