package com.codenzasoft.games.xando

import com.codenzasoft.games.common.Player
import com.codenzasoft.games.xando.strategies.LookAheadStrategy
import com.codenzasoft.games.xando.strategies.RandomStrategy
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class XsAndOsSpec extends Specification {

    def "test creating and playing a tic tac toe game"() {
        setup:
        var p1 = newPlayer("me")
        var p2 = newPlayer("you")
        var game = new XsAndOs.Builder()
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .withId(UUID.randomUUID())
                .build()

        when:
        game.playPiece(p1, 2, 2)
        game.playPiece(p2, 1, 1)

        then:
        !game.isOver()

        when:
        game.playPiece(p1, 1, 2)
        game.playPiece(p2, 1, 3)
        game.playPiece(p1, 3, 2)

        then:
        game.isOver()
        game.getWinner().get().piece() == Piece.X
        game.getWinner().get().positions().stream().allMatch(p -> p.column() == 2)
        game.getWinner().get().positions().stream().map(p -> p.row()).toList() == [1, 2, 3]
    }

    def "test that random strategies will play a game to completion"() {
        setup:
        var p1 = newPlayer("me")
        var p2 = newPlayer("you")
        var s1 = new RandomStrategy(p1, Piece.X)
        var s2 = new RandomStrategy(p2, Piece.O)
        var poll = new PollingConditions(timeout: 5, initialDelay: 0.5, delay: 0.5)

        when:
        var game = new XsAndOs.Builder()
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .addListener(s1)
                .addListener(s2)
                .withId(UUID.randomUUID())
                .build()

        then:
        poll.eventually {
            assert game.isOver()
        }
    }

    def "test that look ahead strategies will play a game to completion"() {
        setup:
        var p1 = newPlayer("me")
        var p2 = newPlayer("you")
        var s1 = new LookAheadStrategy(p1, Piece.X)
        var s2 = new LookAheadStrategy(p2, Piece.O)
        var poll = new PollingConditions(timeout: 60, initialDelay: 0.5, delay: 0.5)

        when:
        var game = new XsAndOs.Builder()
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .addListener(s1)
                .addListener(s2)
                .withId(UUID.randomUUID())
                .build()

        then:
        poll.eventually {
            assert game.isOver()
        }
    }

    def "test creating a tic tac toe game with an initial set of plays/moves"() {
        setup:
        var p1 = newPlayer("me")
        var p2 = newPlayer("you")
        var builder = new XsAndOs.Builder()
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .addPlay(p1, new Position(1,1))
                .addPlay(p2, new Position(1, 2))
                .addPlay(p1, new Position(2, 1))
                .withId(UUID.randomUUID());

        when:
        var game = builder.build()

        then:
        game.getBoard().getPiece(new Position(1,1)).orElse(null) == Piece.X
        game.getBoard().getPiece(new Position(1,2)).orElse(null) == Piece.O
        game.getBoard().getPiece(new Position(2,1)).orElse(null) == Piece.X
    }

    def newPlayer(String name) {
        return new Player(UUID.randomUUID(), name)
    }
}
