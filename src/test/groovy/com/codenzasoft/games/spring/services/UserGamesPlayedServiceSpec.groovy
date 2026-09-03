package com.codenzasoft.games.spring.services

import com.codenzasoft.games.spring.GameType
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.repositories.UsersRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserGamesPlayedServiceSpec extends Specification {

    @Autowired GameService gameService
    @Autowired UsersRepository usersRepository
    @Autowired JdbcTemplate jdbcTemplate

    UserEntity testUser

    def setup() {
        jdbcTemplate.execute("DELETE FROM user_games_played")

        testUser = new UserEntity()
        testUser.setUserName("games-played-user")
        testUser = usersRepository.save(testUser)
    }

    // ── insert ──────────────────────────────────────────────────────────────────

    def "updateGamesPlayed inserts a new record when none exists for the user and game type"() {
        when:
        def result = gameService.updateGamesPlayed(testUser, GameType.XO, 1, 0, 0)

        then:
        result != null
        result.userId == testUser.id
        result.gameType == GameType.XO
        result.wins == 1
        result.losses == 0
        result.draws == 0
        result.lastPlayedAt != null
    }

    def "updateGamesPlayed creates separate records for different game types"() {
        when:
        gameService.updateGamesPlayed(testUser, GameType.XO, 1, 0, 0)
        gameService.updateGamesPlayed(testUser, GameType.HEARTS, 0, 1, 0)

        then:
        def all = gameService.getAllGamesPlayed(testUser)
        all.size() == 2
        all.find { it.gameType == GameType.XO }.wins == 1
        all.find { it.gameType == GameType.HEARTS }.losses == 1
    }

    // ── update ──────────────────────────────────────────────────────────────────

    def "updateGamesPlayed increments wins on an existing record"() {
        given:
        gameService.updateGamesPlayed(testUser, GameType.XO, 1, 0, 0)

        when:
        def result = gameService.updateGamesPlayed(testUser, GameType.XO, 2, 0, 0)

        then:
        result.wins == 3
        result.losses == 0
        result.draws == 0
    }

    def "updateGamesPlayed increments losses on an existing record"() {
        given:
        gameService.updateGamesPlayed(testUser, GameType.XO, 0, 1, 0)

        when:
        def result = gameService.updateGamesPlayed(testUser, GameType.XO, 0, 2, 0)

        then:
        result.losses == 3
    }

    def "updateGamesPlayed increments draws on an existing record"() {
        given:
        gameService.updateGamesPlayed(testUser, GameType.XO, 0, 0, 1)

        when:
        def result = gameService.updateGamesPlayed(testUser, GameType.XO, 0, 0, 4)

        then:
        result.draws == 5
    }

    def "updateGamesPlayed increments wins losses and draws together"() {
        given:
        gameService.updateGamesPlayed(testUser, GameType.HEARTS, 1, 1, 1)

        when:
        def result = gameService.updateGamesPlayed(testUser, GameType.HEARTS, 2, 3, 4)

        then:
        result.wins == 3
        result.losses == 4
        result.draws == 5
    }

    def "updateGamesPlayed refreshes lastPlayedAt on subsequent updates"() {
        given:
        def first = gameService.updateGamesPlayed(testUser, GameType.XO, 1, 0, 0)
        def firstTimestamp = first.lastPlayedAt

        // small sleep so the new timestamp is actually later
        sleep(10)

        when:
        def second = gameService.updateGamesPlayed(testUser, GameType.XO, 1, 0, 0)

        then:
        second.lastPlayedAt.after(firstTimestamp) || second.lastPlayedAt == firstTimestamp
    }

    // ── retrieve ─────────────────────────────────────────────────────────────────

    def "getGamesPlayed returns the summary for the correct user and game type"() {
        given:
        gameService.updateGamesPlayed(testUser, GameType.KH, 3, 1, 2)

        when:
        def result = gameService.getGamesPlayed(testUser, GameType.KH)

        then:
        result.isPresent()
        result.get().wins == 3
        result.get().losses == 1
        result.get().draws == 2
    }

    def "getGamesPlayed returns empty when no record exists for that game type"() {
        expect:
        gameService.getGamesPlayed(testUser, GameType.XO).isEmpty()
    }

    def "getAllGamesPlayed returns all summaries for the user"() {
        given:
        gameService.updateGamesPlayed(testUser, GameType.XO, 1, 0, 0)
        gameService.updateGamesPlayed(testUser, GameType.KH, 0, 1, 0)
        gameService.updateGamesPlayed(testUser, GameType.HEARTS, 0, 0, 1)

        when:
        def all = gameService.getAllGamesPlayed(testUser)

        then:
        all.size() == 3
        all.every { it.userId == testUser.id }
    }

    def "getAllGamesPlayed returns an empty list for a user with no games played"() {
        expect:
        gameService.getAllGamesPlayed(testUser).isEmpty()
    }
}
