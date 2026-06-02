package com.codenzasoft.games.spring.services.kh

import com.codenzasoft.games.common.Player
import com.codenzasoft.games.kingshand.GameEngine
import com.codenzasoft.games.kingshand.GameState
import com.codenzasoft.games.kingshand.Season
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.entities.kh.KhGameResultEntity
import com.codenzasoft.games.spring.repositories.GamesRepository
import com.codenzasoft.games.spring.repositories.UsersRepository
import com.codenzasoft.games.spring.repositories.kh.KhGameResultRepository
import spock.lang.Specification

class KhGamePersistenceServiceSpec extends Specification {

    KhGameResultRepository resultRepository = Mock()
    GamesRepository gamesRepository = Mock()
    UsersRepository usersRepository = Mock()

    KhGamePersistenceService service

    UUID gameId = UUID.randomUUID()
    UserEntity testUser = new UserEntity()

    def setup() {
        service = new KhGamePersistenceService()
        service.khGameResultRepository = resultRepository
        service.gamesRepository = gamesRepository
        service.usersRepository = usersRepository

        testUser.setUserName("test-player")
        usersRepository.findByUserName("test-player") >> testUser
        gamesRepository.findById(gameId) >> Optional.empty()
    }

    // -------------------------------------------------------------------------
    // Guard conditions — nothing should be saved
    // -------------------------------------------------------------------------

    def "no-op when game is not over"() {
        given:
        def game = gameWithState(gameOver: false, year: 3, season: Season.FALL, peakPop: 80.0)

        when:
        service.saveResult(game)

        then:
        0 * resultRepository.save(_)
    }

    def "no-op when result already exists for this game (idempotency)"() {
        given:
        def game = gameWithState(gameOver: true, year: 2, season: Season.WINTER, peakPop: 60.0)
        resultRepository.existsById(gameId) >> true

        when:
        service.saveResult(game)

        then:
        0 * resultRepository.save(_)
    }

    def "no-op when player username cannot be resolved to a user"() {
        given: "a game whose player name has no matching user record"
        def game = gameWithState(gameOver: true, year: 2, season: Season.SUMMER, peakPop: 40.0, playerName: "ghost-player")
        resultRepository.existsById(gameId) >> false
        // "ghost-player" has no stub so the mock returns null by default

        when:
        service.saveResult(game)

        then:
        0 * resultRepository.save(_)
    }

    // -------------------------------------------------------------------------
    // Happy path — result saved with correct values
    // -------------------------------------------------------------------------

    def "saves result with correct year, season, and peak population"() {
        given:
        def game = gameWithState(gameOver: true, year: 4, season: Season.FALL, peakPop: 123.5)
        resultRepository.existsById(gameId) >> false

        when:
        service.saveResult(game)

        then:
        1 * resultRepository.save({ KhGameResultEntity r ->
            r.gameId == gameId &&
            r.yearSurvived == 4 &&
            r.seasonSurvived == Season.FALL &&
            r.peakPopulation == 123.5 &&
            r.user == testUser
        })
    }

    def "season_order is derived correctly from the season enum"() {
        given:
        def game = gameWithState(gameOver: true, year: 1, season: Season.WINTER, peakPop: 20.0)
        resultRepository.existsById(gameId) >> false

        when:
        service.saveResult(game)

        then:
        1 * resultRepository.save({ KhGameResultEntity r ->
            r.seasonOrder == Season.WINTER.ordinal()  // 3
        })
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private GameEngine gameWithState(Map args) {
        def state = GameState.builder()
            .gameOver(args.gameOver as boolean)
            .year(args.year as int)
            .curSeason(args.season as Season)
            .peakPopulation(args.peakPop as double)
            .build()

        def name = args.playerName ?: "test-player"
        def player = new Player(UUID.randomUUID(), name as String)
        return new GameEngine(gameId, player, state)
    }
}
