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

    def setup() {
        jdbcTemplate.execute("DELETE FROM xo_game_moves")
        jdbcTemplate.execute("DELETE FROM xo_game_pieces")
        jdbcTemplate.execute("DELETE FROM game_players")
        jdbcTemplate.execute("DELETE FROM games")

        testUser = new UserEntity()
        testUser.setUserName("test-limit-user")
        testUser = usersRepository.save(testUser)
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

    def "createKhGame persists a new KH game with the given user as player"() {

        when:
        def entity = gameService.createKhGame(testUser)

        then:
        entity instanceof KhGameEntity
        entity.id != null
        entity.gameType == GameType.KH
        entity.players == [testUser]
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
        result.get().players.size() == 1
        result.get().players[0].id() == testUser.id
        result.get().players[0].name() == "test-limit-user"
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

    def "createHeartsGame persists a new Hearts game with the user and three bots as players"() {
        when:
        def entity = gameService.createHeartsGame(testUser)

        then:
        entity instanceof HeartsGameEntity
        entity.id != null
        entity.gameType == GameType.HEARTS
        entity.players.size() == 4
        entity.players[0] == testUser
        entity.players[1..-1].every { it.isBot() }
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

}
