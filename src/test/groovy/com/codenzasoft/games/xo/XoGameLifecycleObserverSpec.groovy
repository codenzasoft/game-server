package com.codenzasoft.games.xo

import com.codenzasoft.games.common.GameLifecycleObserver
import com.codenzasoft.games.common.Player
import spock.lang.Specification

import java.util.concurrent.Executor

/**
 * Verifies XO's {@link XoGame} notifies the common {@link GameLifecycleObserver} (GAME-117)
 * alongside its existing XO-specific {@link XoGameListener} notifications. Mirrors the structure of
 * {@code GameEngineLifecycleObserverSpec} (KH, GAME-116).
 */
class XoGameLifecycleObserverSpec extends Specification {

    private static Player newPlayer(String name) {
        new Player(UUID.randomUUID(), name)
    }

    def "construction fires gameStarted on a fresh game"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def p1 = newPlayer("me")
        def p2 = newPlayer("you")

        when:
        new XoGame.Builder()
                .withId(UUID.randomUUID())
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .withNotificationExecutor({ it.run() } as Executor)
                .addLifecycleObserver(observer)
                .build()

        then:
        1 * observer.gameStarted(_)
    }

    def "rebuilding a game from replayed move history fires gameStarted once and never gameResumed"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def p1 = newPlayer("me")
        def p2 = newPlayer("you")

        when:
        new XoGame.Builder()
                .withId(UUID.randomUUID())
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .withNotificationExecutor({ it.run() } as Executor)
                .addPlay(p1, Position.of(1, 1))
                .addPlay(p2, Position.of(2, 1))
                .addPlay(p1, Position.of(1, 2))
                .addLifecycleObserver(observer)
                .build()

        then:
        1 * observer.gameStarted(_)
        0 * observer.gameResumed(_)
    }

    def "replaying move history during rebuild does not spuriously fire gameOver"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def p1 = newPlayer("me")
        def p2 = newPlayer("you")

        when:
        // Replays a full winning game (X wins row 1) - mirrors GameService rebuilding an already
        // finished XO game from persisted move history on a cache miss. Lifecycle observers must
        // only be wired up AFTER this replay completes, or this would double-fire gameOver.
        new XoGame.Builder()
                .withId(UUID.randomUUID())
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .withNotificationExecutor({ it.run() } as Executor)
                .addPlay(p1, Position.of(1, 1))
                .addPlay(p2, Position.of(2, 1))
                .addPlay(p1, Position.of(1, 2))
                .addPlay(p2, Position.of(2, 2))
                .addPlay(p1, Position.of(1, 3))
                .addLifecycleObserver(observer)
                .build()

        then:
        0 * observer.gameOver(_)
    }

    def "winning move played live fires gameOver exactly once"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def p1 = newPlayer("me")
        def p2 = newPlayer("you")
        def game = new XoGame.Builder()
                .withId(UUID.randomUUID())
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .withNotificationExecutor({ it.run() } as Executor)
                .addPlay(p1, Position.of(1, 1))
                .addPlay(p2, Position.of(2, 1))
                .addPlay(p1, Position.of(1, 2))
                .addPlay(p2, Position.of(2, 2))
                .addLifecycleObserver(observer)
                .build()

        when:
        game.playPiece(p1, 1, 3)

        then:
        1 * observer.gameOver(game)
        game.isOver()
    }

    def "quit fires gameQuit and not gameOver"() {
        setup:
        def observer = Mock(GameLifecycleObserver)
        def p1 = newPlayer("me")
        def p2 = newPlayer("you")
        def game = new XoGame.Builder()
                .withId(UUID.randomUUID())
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .withNotificationExecutor({ it.run() } as Executor)
                .addLifecycleObserver(observer)
                .build()

        when:
        game.quit(p1)

        then:
        1 * observer.gameQuit(game)
        0 * observer.gameOver(_)
    }

    def "all registered observers receive gameStarted"() {
        setup:
        def observer1 = Mock(GameLifecycleObserver)
        def observer2 = Mock(GameLifecycleObserver)
        def p1 = newPlayer("me")
        def p2 = newPlayer("you")

        when:
        new XoGame.Builder()
                .withId(UUID.randomUUID())
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .withNotificationExecutor({ it.run() } as Executor)
                .addLifecycleObserver(observer1)
                .addLifecycleObserver(observer2)
                .build()

        then:
        1 * observer1.gameStarted(_)
        1 * observer2.gameStarted(_)
    }

    def "legacy XoGameListener and the common GameLifecycleObserver both receive notifications"() {
        setup:
        def xoListener = Mock(XoGameListener)
        def lifecycleObserver = Mock(GameLifecycleObserver)
        def p1 = newPlayer("me")
        def p2 = newPlayer("you")

        when:
        def game = new XoGame.Builder()
                .withId(UUID.randomUUID())
                .addPlayer(p1, Piece.X)
                .addPlayer(p2, Piece.O)
                .withNotificationExecutor({ it.run() } as Executor)
                .addListener(xoListener)
                .addLifecycleObserver(lifecycleObserver)
                .build()

        then:
        1 * xoListener.gameStarted(game, _)
        1 * lifecycleObserver.gameStarted(_)
    }
}
