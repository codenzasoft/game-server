package com.codenzasoft.games.hearts

import com.codenzasoft.games.common.Player
import com.codenzasoft.games.common.cards.Suit
import spock.lang.Specification

import java.util.concurrent.Executor

class HeartsGameRulesSpec extends Specification {

    def p1 = new Player(UUID.randomUUID(), "alice")
    def p2 = new Player(UUID.randomUUID(), "bob")
    def p3 = new Player(UUID.randomUUID(), "carol")
    def p4 = new Player(UUID.randomUUID(), "dave")

    def buildGame() {
        new HeartsGame.Builder()
                .withPlayers([p1, p2, p3, p4])
                .withId(UUID.randomUUID())
                .withNotificationExecutor({ it.run() } as Executor)
                .build()
    }

    /** Sets up passes (handles KEEPER rounds) and executes the pass. */
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

    def findNextPlayer(HeartsGame game) {
        def state = game.gameState
        def nextName = state.currentRound.get().getNextPlayer(state).get()
        [p1, p2, p3, p4].find { it.name() == nextName }
    }

    def "getValidCards on the first trick before any card is played returns only the 2 of clubs"() {
        def game = buildGame()
        game.startNewRound()
        selectPassesAndPass(game)
        game.startNewTrick()
        def leader = findNextPlayer(game)

        expect:
        game.getValidCards(leader) == [HeartsGame.TWO_OF_CLUBS] as Set
    }

    def "playing the 2 of clubs on trick 1 succeeds"() {
        def game = buildGame()
        game.startNewRound()
        selectPassesAndPass(game)
        game.startNewTrick()
        def leader = findNextPlayer(game)

        when:
        game.playCard(leader, HeartsGame.TWO_OF_CLUBS)

        then:
        notThrown(IllegalStateException)
    }

    def "playing a card not in the valid set throws"() {
        def game = buildGame()
        game.startNewRound()
        selectPassesAndPass(game)
        game.startNewTrick()
        def leader = findNextPlayer(game)
        def invalidCard = game.gameState.currentRound.get().hands().get(leader.name())
                .find { it != HeartsGame.TWO_OF_CLUBS }

        when:
        game.playCard(leader, invalidCard)

        then:
        thrown(IllegalStateException)
    }

    def "getValidCards after suit is led restricts to led suit if player has it"() {
        def game = buildGame()
        game.startNewRound()
        selectPassesAndPass(game)
        game.startNewTrick()

        // Play 2♣ to establish clubs as the led suit
        game.playCard(findNextPlayer(game), HeartsGame.TWO_OF_CLUBS)

        def nextPlayer = findNextPlayer(game)
        def hand = game.gameState.currentRound.get().hands().get(nextPlayer.name())
        def clubs = hand.findAll { it.suit == Suit.CLUBS } as Set

        when:
        def validCards = game.getValidCards(nextPlayer)

        then:
        clubs.isEmpty() || validCards == clubs
    }

    def "getValidCards when leading trick 2 or later excludes hearts when hearts not broken"() {
        def game = buildGame()
        game.startNewRound()
        selectPassesAndPass(game)

        // Play out trick 1 fully
        game.startNewTrick()
        4.times {
            def player = findNextPlayer(game)
            def validCards = game.getValidCards(player)
            game.playCard(player, validCards.first())
        }

        // Verify no hearts have been played
        def playedCards = game.gameState.currentRound.get().playedCards
        def heartsPlayed = playedCards.any { it.suit == Suit.HEARTS }

        game.startNewTrick()
        def leader = findNextPlayer(game)
        def hand = game.gameState.currentRound.get().hands().get(leader.name())
        def nonHearts = hand.findAll { it.suit != Suit.HEARTS }

        when:
        def validCards = game.getValidCards(leader)

        then:
        // If hearts haven't been played and the player has non-hearts, hearts must be excluded
        heartsPlayed || nonHearts.isEmpty() || validCards.every { it.suit != Suit.HEARTS }
    }

    def "getValidCards when leading includes hearts after a heart has been played"() {
        def game = buildGame()
        game.startNewRound()
        selectPassesAndPass(game)

        // Play tricks until a heart is played (void in led suit → can discard a heart)
        boolean heartPlayed = false
        while (!heartPlayed) {
            game.startNewTrick()
            4.times {
                def player = findNextPlayer(game)
                def validCards = game.getValidCards(player)
                def card = validCards.first()
                game.playCard(player, card)
                if (card.suit == Suit.HEARTS) heartPlayed = true
            }
            if (game.gameState.currentRound.get().complete) break
        }

        if (!heartPlayed) return  // hearts never broken in this deal; skip

        // Start a new trick and confirm leader can lead hearts
        game.startNewTrick()
        def leader = findNextPlayer(game)
        def hand = game.gameState.currentRound.get().hands().get(leader.name())
        def hearts = hand.findAll { it.suit == Suit.HEARTS }

        when:
        def validCards = game.getValidCards(leader)

        then:
        hearts.isEmpty() || validCards.containsAll(hearts)
    }
}
