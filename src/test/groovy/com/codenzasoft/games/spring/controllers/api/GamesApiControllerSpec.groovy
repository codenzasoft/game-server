package com.codenzasoft.games.spring.controllers.api

import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.repositories.UsersRepository
import com.codenzasoft.games.spring.services.GameService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

import java.time.Instant

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class GamesApiControllerSpec extends Specification {

    @Autowired WebApplicationContext context
    @Autowired UsersRepository usersRepository
    @Autowired GameService gameService
    @Autowired JdbcTemplate jdbcTemplate

    MockMvc mockMvc

    UserEntity testUser
    UserEntity otherUser

    def setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build()

        jdbcTemplate.execute("DELETE FROM game_players")
        jdbcTemplate.execute("DELETE FROM games")

        testUser = new UserEntity()
        testUser.setUserName("games-api-user")
        testUser.setEmail("games-api@example.com")
        testUser.setEmailVerifiedAt(Instant.now())
        testUser = usersRepository.save(testUser)

        otherUser = new UserEntity()
        otherUser.setUserName("games-api-other")
        otherUser.setEmail("games-api-other@example.com")
        otherUser.setEmailVerifiedAt(Instant.now())
        otherUser = usersRepository.save(otherUser)
    }

    // ── GET /api/games ────────────────────────────────────────────────────────

    def "listGames returns an empty list when the user has no active games"() {
        when:
        def result = mockMvc.perform(
            get("/api/games").with(user("games-api-user"))
        )

        then:
        result.andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath('$').isArray())
            .andExpect(jsonPath('$.length()').value(0))
    }

    def "listGames returns the user's active games"() {
        given:
        gameService.createHeartsGame(testUser)

        when:
        def result = mockMvc.perform(
            get("/api/games").with(user("games-api-user"))
        )

        then:
        result.andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(1))
            .andExpect(jsonPath('$[0].gameType').value("HEARTS"))
            .andExpect(jsonPath('$[0].id').exists())
    }

    def "listGames does not include completed games"() {
        given:
        def game = gameService.createHeartsGame(testUser)
        gameService.quitGame(game.id, testUser)

        when:
        def result = mockMvc.perform(
            get("/api/games").with(user("games-api-user"))
        )

        then:
        result.andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(0))
    }

    def "listGames returns 401 when not authenticated"() {
        when:
        def result = mockMvc.perform(get("/api/games"))

        then:
        result.andExpect(status().isUnauthorized())
    }

    // ── POST /api/games/hearts ────────────────────────────────────────────────

    def "createHeartsGame returns 201 and a game id"() {
        when:
        def result = mockMvc.perform(
            post("/api/games/hearts").with(user("games-api-user"))
        )

        then:
        result.andExpect(status().isCreated())
            .andExpect(jsonPath('$.id').exists())
    }

    def "createHeartsGame creates a game visible in the games list"() {
        given:
        mockMvc.perform(post("/api/games/hearts").with(user("games-api-user")))

        when:
        def result = mockMvc.perform(
            get("/api/games").with(user("games-api-user"))
        )

        then:
        result.andExpect(jsonPath('$.length()').value(1))
            .andExpect(jsonPath('$[0].gameType').value("HEARTS"))
    }

    def "createHeartsGame returns 409 when user is at the game limit"() {
        given:
        GameService.MAX_ACTIVE_GAMES.times {
            gameService.createHeartsGame(testUser)
        }

        when:
        def result = mockMvc.perform(
            post("/api/games/hearts").with(user("games-api-user"))
        )

        then:
        result.andExpect(status().isConflict())
            .andExpect(jsonPath('$.error').exists())
    }

    def "createHeartsGame returns 401 when not authenticated"() {
        when:
        def result = mockMvc.perform(post("/api/games/hearts"))

        then:
        result.andExpect(status().isUnauthorized())
    }

    // ── POST /api/games/hearts/multiplayer ────────────────────────────────────

    def "createMultiplayerHeartsGame returns 201 and a game id"() {
        when:
        def result = mockMvc.perform(
            post("/api/games/hearts/multiplayer").with(user("games-api-user"))
        )

        then:
        result.andExpect(status().isCreated())
            .andExpect(jsonPath('$.id').exists())
    }

    def "createMultiplayerHeartsGame creates a pending game with only the host as player"() {
        when:
        def result = mockMvc.perform(
            post("/api/games/hearts/multiplayer").with(user("games-api-user"))
        )

        then:
        result.andExpect(status().isCreated())

        and: "game list shows the pending game"
        mockMvc.perform(get("/api/games").with(user("games-api-user")))
            .andExpect(jsonPath('$.length()').value(1))
            .andExpect(jsonPath('$[0].players.length()').value(1))
    }

    // ── DELETE /api/games/{id} ────────────────────────────────────────────────

    def "deleteGame returns 204 and the game is no longer in the active list"() {
        given:
        def game = gameService.createHeartsGame(testUser)

        when:
        def result = mockMvc.perform(
            delete("/api/games/${game.id}").with(user("games-api-user"))
        )

        then:
        result.andExpect(status().isNoContent())

        and:
        mockMvc.perform(get("/api/games").with(user("games-api-user")))
            .andExpect(jsonPath('$.length()').value(0))
    }

    def "deleteGame returns 403 when the user is not a player in the game"() {
        given:
        def game = gameService.createHeartsGame(testUser)

        when:
        def result = mockMvc.perform(
            delete("/api/games/${game.id}").with(user("games-api-other"))
        )

        then:
        result.andExpect(status().isForbidden())
    }

    def "deleteGame returns 401 when not authenticated"() {
        given:
        def game = gameService.createHeartsGame(testUser)

        when:
        def result = mockMvc.perform(delete("/api/games/${game.id}"))

        then:
        result.andExpect(status().isUnauthorized())
    }
}
