package com.codenzasoft.games.spring.services

import com.codenzasoft.games.hearts.HeartsGame
import com.codenzasoft.games.kingshand.GameEngine
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.entities.hearts.HeartsGameEntity
import com.codenzasoft.games.spring.entities.kh.KhGameEntity
import com.codenzasoft.games.spring.repositories.GamesRepository
import com.codenzasoft.games.common.GameType
import com.codenzasoft.games.spring.repositories.UsersRepository
import com.codenzasoft.games.xo.Piece
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GameServiceSpec extends Specification {

    @Autowired GameService gameService
    @Autowired UsersRepository usersRepository
    @Autowired JdbcTemplate jdbcTemplate
    @Autowired GamesRepository gamesRepository
    @Autowired GameCache gameCache

    UserEntity testUser
    UserEntity testUser2

    def setup() {
        jdbcTemplate.execute("DELETE FROM xo_game_moves")
        jdbcTemplate.execute("DELETE FROM xo_game_pieces")
        jdbcTemplate.execute("DELETE FROM game_players")
        jdbcTemplate.execute("DELETE FROM games")

        testUser = new UserEntity()
        testUser.setUserName("test-limit-user")
        testUser = usersRepository.save(testUser)

        testUser2 = new UserEntity()
        testUser2.setUserName("test-user-2")
        testUser2 = usersRepository.save(testUser2)
    }

    def "isAtGameLimit returns false when user has no active games"() {
        expect:
        !gameService.isAtGameLimit(testUser)
    }

    def "isAtGameLimit returns false when user is below the limit"() {
        given: "user has one fewer game than the limit"
        (GameService.MAX_ACTIVE_GAMES - 1).times {
            gameService.createXoGame(testUser, Piece.X, Piece.X)
        }

        expect:
        !gameService.isAtGameLimit(testUser)
    }

    def "isAtGameLimit returns true when user is at the limit"() {
        given:
        GameService.MAX_ACTIVE_GAMES.times {
            gameService.createXoGame(testUser, Piece.X, Piece.X)
        }

        expect:
        gameService.isAtGameLimit(testUser)
    }

    def "creating the 10th game succeeds"() {
        given:
        (GameService.MAX_ACTIVE_GAMES - 1).times {
            gameService.createXoGame(testUser, Piece.X, Piece.X)
        }

        when:
        def game = gameService.createXoGame(testUser, Piece.X, Piece.X)

        then:
        game != null
        game.id != null
    }

    def "creating a game beyond the limit throws IllegalStateException for xo vs-bot"() {
        given: "user is already at the active game limit"
        GameService.MAX_ACTIVE_GAMES.times {
            gameService.createXoGame(testUser, Piece.X, Piece.X)
        }

        when:
        gameService.createXoGame(testUser, Piece.X, Piece.X)

        then:
        thrown(IllegalStateException)
    }

    def "creating a kh game beyond the limit throws IllegalStateException"() {
        given: "user is already at the active game limit"
        GameService.MAX_ACTIVE_GAMES.times {
            gameService.createXoGame(testUser, Piece.X, Piece.X)
        }

        when:
        gameService.createKhGame(testUser)

        then:
        thrown(IllegalStateException)
    }

    def "completed games do not count toward the active game limit"() {
        given: "user has MAX_ACTIVE_GAMES games, all of which are then completed"
        GameService.MAX_ACTIVE_GAMES.times {
            gameService.createXoGame(testUser, Piece.X, Piece.X)
        }
        jdbcTemplate.execute("UPDATE games SET over_at = NOW() WHERE over_at IS NULL AND quit_at IS NULL")

        expect: "limit is no longer reached"
        !gameService.isAtGameLimit(testUser)

        and: "a new game can be created"
        gameService.createXoGame(testUser, Piece.X, Piece.X) != null

    }

    def "createKhGame persists a new KH game with the user at seat 0 and a bot at seat 1"() {

        when:
        def entity = gameService.createKhGame(testUser)

        then:
        entity instanceof KhGameEntity
        entity.id != null
        entity.gameType == GameType.KH
        entity.players.size() == 2
        entity.players[0] == testUser
        entity.players[1].isBot()
    }

    def "getOrBuildKhGame fetches from DB on first call and adds game to cache"() {
        given:
        def game = gameService.createKhGame(testUser)
        def gameId = game.id

        when:
        def result = gameService.getOrBuildKhGame(gameId)

        then:
        result.isPresent()
        result.get() instanceof GameEngine
        result.get().id == gameId
        result.get().players.size() == 2
        result.get().players[0].id() == testUser.id
        result.get().players[0].name() == "test-limit-user"
        result.get().players[1].name() != "test-limit-user"   // seat 1 is the bot
    }

    def "getOrBuildKhGame adds the built game to the cache"() {
        given:
        def game = gameService.createKhGame(testUser)
        def gameId = game.id

        when:
        gameService.getOrBuildKhGame(gameId)

        then:
        gameCache.getGameOfType(gameId, GameType.KH).isPresent()
    }

    def "createPendingKhGame seats only the host with no bot"() {
        when:
        def entity = gameService.createPendingKhGame(testUser)

        then:
        entity instanceof KhGameEntity
        entity.gameType == GameType.KH
        entity.players.size() == 1
        entity.players[0] == testUser
    }

    def "getOrBuildKhGame is empty while pending and builds a two-human match once joined"() {
        given:
        def gameId = gameService.createPendingKhGame(testUser).id

        expect: "a pending invite (one human seated) is not built or started"
        gameService.getOrBuildKhGame(gameId).isEmpty()

        when: "a second human joins, filling seat 1"
        gameService.joinKhGame(gameId, testUser2)

        then: "the match now builds with both humans and no bot"
        def result = gameService.getOrBuildKhGame(gameId)
        result.isPresent()
        result.get().players.size() == 2
        result.get().players[0].id() == testUser.id
        result.get().players[1].id() == testUser2.id
        !result.get().players.any { it.name().endsWith("-bot") }
    }

    def "joinKhGame rejects a third player once the match is full"() {
        given:
        def gameId = gameService.createPendingKhGame(testUser).id
        gameService.joinKhGame(gameId, testUser2)
        def third = new UserEntity()
        third.setUserName("third-user")
        third = usersRepository.save(third)

        when:
        gameService.joinKhGame(gameId, third)

        then:
        thrown(IllegalStateException)
    }

    def "joinKhGame is a no-op when the host opens their own pending game"() {
        given:
        def gameId = gameService.createPendingKhGame(testUser).id

        when:
        def result = gameService.joinKhGame(gameId, testUser)

        then:
        result.players.size() == 1
        result.players[0] == testUser
    }

    def "createHeartsGame persists a new Hearts game with the user and three bots as players"() {
        when:
        def entity = gameService.createHeartsGame(testUser)

        then:
        entity instanceof HeartsGameEntity
        entity.id != null
        entity.gameType == GameType.HEARTS
        entity.players.size() == 4
        entity.players[0] == testUser
        entity.players[1..3].every { it.isBot() }
    }

    def "getOrBuildHeartsGame fetches from DB on first call and returns a HeartsGame"() {
        given:
        def entity = gameService.createHeartsGame(testUser)
        def gameId = entity.id

        when:
        def result = gameService.getOrBuildHeartsGame(gameId)

        then:
        result.isPresent()
        result.get() instanceof HeartsGame
        result.get().id == gameId
        result.get().players.size() == 4
        result.get().players[0].name() == testUser.username
    }

    def "getOrBuildHeartsGame adds the built game to the cache"() {
        given:
        def entity = gameService.createHeartsGame(testUser)
        def gameId = entity.id

        when:
        gameService.getOrBuildHeartsGame(gameId)

        then:
        gameCache.getGameOfType(gameId, GameType.HEARTS).isPresent()
    }

    // ── createPendingHeartsGame ───────────────────────────────────────────────

    def "createPendingHeartsGame creates a Hearts game with only the host as a player"() {
        when:
        def entity = gameService.createPendingHeartsGame(testUser)

        then:
        entity instanceof HeartsGameEntity
        entity.id != null
        entity.gameType == GameType.HEARTS
        entity.players.size() == 1
        entity.players[0] == testUser
    }

    def "createPendingHeartsGame does not assign any bots"() {
        when:
        def entity = gameService.createPendingHeartsGame(testUser)

        then:
        entity.players.every { !it.isBot() }
    }

    def "createPendingHeartsGame throws when the user is at the game limit"() {
        given:
        GameService.MAX_ACTIVE_GAMES.times {
            gameService.createXoGame(testUser, Piece.X, Piece.X)
        }

        when:
        gameService.createPendingHeartsGame(testUser)

        then:
        thrown(IllegalStateException)
    }

    def "getOrBuildHeartsGame returns empty for a pending game with fewer than 4 players"() {
        given:
        def entity = gameService.createPendingHeartsGame(testUser)

        when:
        def result = gameService.getOrBuildHeartsGame(entity.id)

        then:
        result.isEmpty()
    }

    // ── joinHeartsGame ────────────────────────────────────────────────────────

    def "joinHeartsGame adds a new player to a pending game"() {
        given:
        def entity = gameService.createPendingHeartsGame(testUser)

        when:
        def updated = gameService.joinHeartsGame(entity.id, testUser2)

        then:
        updated.players.size() == 2
        updated.players.contains(testUser)
        updated.players.contains(testUser2)
    }

    def "joinHeartsGame is a no-op when the user is already a player"() {
        given:
        def entity = gameService.createPendingHeartsGame(testUser)

        when:
        def updated = gameService.joinHeartsGame(entity.id, testUser)

        then:
        updated.players.size() == 1
    }

    def "joinHeartsGame throws when the game already has 4 players"() {
        given:
        def entity = gameService.createHeartsGame(testUser)

        when:
        gameService.joinHeartsGame(entity.id, testUser2)

        then:
        thrown(IllegalStateException)
    }

    def "joinHeartsGame throws when the game is completed"() {
        given:
        def entity = gameService.createPendingHeartsGame(testUser)
        gameService.quitGame(entity.id, testUser)

        when:
        gameService.joinHeartsGame(entity.id, testUser2)

        then:
        thrown(IllegalStateException)
    }

    def "joinHeartsGame throws when the game does not exist"() {
        when:
        gameService.joinHeartsGame(UUID.randomUUID(), testUser)

        then:
        thrown(IllegalStateException)
    }

    // ── startHeartsGame ───────────────────────────────────────────────────────

    def "startHeartsGame fills the remaining seats with bots"() {
        given:
        def entity = gameService.createPendingHeartsGame(testUser)

        when:
        def started = gameService.startHeartsGame(entity.id, testUser)

        then:
        started.players.size() == 4
        started.players[0] == testUser
        started.players[1..3].every { it.isBot() }
    }

    def "startHeartsGame with two human players adds exactly two bots"() {
        given:
        def entity = gameService.createPendingHeartsGame(testUser)
        gameService.joinHeartsGame(entity.id, testUser2)

        when:
        def started = gameService.startHeartsGame(entity.id, testUser)

        then:
        started.players.size() == 4
        started.players.count { !it.isBot() } == 2
        started.players.count { it.isBot() } == 2
    }

    def "startHeartsGame is a no-op when the game already has 4 players"() {
        given:
        def entity = gameService.createHeartsGame(testUser)

        when:
        def started = gameService.startHeartsGame(entity.id, testUser)

        then:
        started.players.size() == 4
    }

    def "startHeartsGame throws when the requesting user is not a player"() {
        given:
        def entity = gameService.createPendingHeartsGame(testUser)

        when:
        gameService.startHeartsGame(entity.id, testUser2)

        then:
        thrown(IllegalStateException)
    }

    def "startHeartsGame throws when the game is completed"() {
        given:
        def entity = gameService.createPendingHeartsGame(testUser)
        gameService.quitGame(entity.id, testUser)

        when:
        gameService.startHeartsGame(entity.id, testUser)

        then:
        thrown(IllegalStateException)
    }

    def "getOrBuildHeartsGame succeeds after startHeartsGame fills the roster"() {
        given:
        def entity = gameService.createPendingHeartsGame(testUser)
        gameService.startHeartsGame(entity.id, testUser)

        when:
        def result = gameService.getOrBuildHeartsGame(entity.id)

        then:
        result.isPresent()
        result.get() instanceof HeartsGame
        result.get().players.size() == 4
    }

}
