package com.codenzasoft.games.spring.services

import com.codenzasoft.games.kingshand.Card
import com.codenzasoft.games.kingshand.CardTarget
import com.codenzasoft.games.kingshand.Effect
import com.codenzasoft.games.kingshand.FrequencyType
import com.codenzasoft.games.kingshand.GameEngine
import com.codenzasoft.games.kingshand.GameState
import com.codenzasoft.games.kingshand.Phase
import com.codenzasoft.games.kingshand.ResourceTarget
import com.codenzasoft.games.kingshand.Season
import com.codenzasoft.games.kingshand.ValueType
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.repositories.GamesRepository
import com.codenzasoft.games.spring.repositories.UsersRepository
import com.codenzasoft.games.spring.repositories.kh.KhGameStateRepository
import com.codenzasoft.games.spring.services.kh.KhPersistenceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

/**
 * Mirrors {@code HeartsPersistenceServiceSpec}, adapted for King's Hand's single-JSON-snapshot
 * model (GAME-81): one row per game, overwritten on every save, rather than Hearts' granular
 * per-round/trick rows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KhPersistenceServiceSpec extends Specification {

    @Autowired KhPersistenceService khPersistenceService
    @Autowired GameService gameService
    @Autowired UsersRepository usersRepository
    @Autowired KhGameStateRepository khGameStateRepository
    @Autowired GamesRepository gamesRepository
    @Autowired JdbcTemplate jdbcTemplate

    UserEntity testUser

    def setup() {
        jdbcTemplate.execute("DELETE FROM kh_game_state")
        jdbcTemplate.execute("DELETE FROM hearts_tricks")
        jdbcTemplate.execute("DELETE FROM hearts_round_passes")
        jdbcTemplate.execute("DELETE FROM hearts_round_hands")
        jdbcTemplate.execute("DELETE FROM xo_game_moves")
        jdbcTemplate.execute("DELETE FROM xo_game_pieces")
        jdbcTemplate.execute("DELETE FROM game_players")
        jdbcTemplate.execute("DELETE FROM games")

        testUser = new UserEntity()
        testUser.setUserName("kh-persist-user")
        testUser = usersRepository.save(testUser)
    }

    // ── gameStarted ──────────────────────────────────────────────────────────

    def "gameStarted persists the state as a JSON snapshot"() {
        given:
        def entity = gameService.createKhGame(testUser)
        def state = GameState.newState()
        def engine = dummyEngine(entity, state)

        when:
        khPersistenceService.gameStarted(engine, state)

        then:
        khGameStateRepository.findById(entity.id).isPresent()
    }

    def "gameStarted does not mark the game over"() {
        given:
        def entity = gameService.createKhGame(testUser)
        def state = GameState.newState()
        def engine = dummyEngine(entity, state)

        when:
        khPersistenceService.gameStarted(engine, state)

        then:
        !gamesRepository.findById(entity.id).get().isOver()
    }

    // ── save ─────────────────────────────────────────────────────────────────

    def "save persists the engine's current state"() {
        given:
        def entity = gameService.createKhGame(testUser)
        def state = GameState.builder().population(15.0).production(120.0).build()
        def engine = dummyEngine(entity, state)

        when:
        khPersistenceService.save(engine)

        then:
        def restored = khPersistenceService.reconstructState(entity.id)
        restored.isPresent()
        restored.get().getPopulation() == 15.0
        restored.get().getProduction() == 120.0
    }

    def "save overwrites the previous snapshot rather than accumulating rows"() {
        given:
        def entity = gameService.createKhGame(testUser)
        def firstState = GameState.builder().population(20.0).build()
        def secondState = GameState.builder().population(5.0).build()

        when:
        khPersistenceService.save(dummyEngine(entity, firstState))
        khPersistenceService.save(dummyEngine(entity, secondState))

        then:
        khGameStateRepository.count() == 1
        khPersistenceService.reconstructState(entity.id).get().getPopulation() == 5.0
    }

    // ── gameOver ─────────────────────────────────────────────────────────────

    def "gameOver persists the state and marks the game over"() {
        given:
        def entity = gameService.createKhGame(testUser)
        def state = GameState.builder().population(0.0).gameOver(true).build()
        def engine = dummyEngine(entity, state)

        when:
        khPersistenceService.gameOver(engine, state)

        then:
        khPersistenceService.reconstructState(entity.id).get().isGameOver()
        gamesRepository.findById(entity.id).get().isOver()
    }

    // ── reconstructState ─────────────────────────────────────────────────────

    def "reconstructState returns empty when no snapshot has been persisted"() {
        given:
        def entity = gameService.createKhGame(testUser)

        when:
        def result = khPersistenceService.reconstructState(entity.id)

        then:
        result.isEmpty()
    }

    def "reconstructState round-trips resource values, season/phase, and year"() {
        given:
        def entity = gameService.createKhGame(testUser)
        def state = GameState.builder()
                .population(12.0).production(85.0).food(40.0)
                .curSeason(Season.AUTUMN)
                .curPhase(Phase.PLAY)
                .year(3)
                .build()
        khPersistenceService.save(dummyEngine(entity, state))

        when:
        def result = khPersistenceService.reconstructState(entity.id)

        then:
        result.isPresent()
        def restored = result.get()
        restored.getPopulation() == 12.0
        restored.getProduction() == 85.0
        restored.getFood() == 40.0
        restored.getCurSeason() == Season.AUTUMN
        restored.getCurPhase() == Phase.PLAY
        restored.getYear() == 3
    }

    def "reconstructState round-trips a hand card with both ResourceTarget and CardTarget effects"() {
        given:
        def entity = gameService.createKhGame(testUser)
        def card = new Card(
                "Engine Builder",
                5.0, 10.0,
                FrequencyType.RECURRING,
                [
                        new Effect(ResourceTarget.PRODUCTION, 4.0, ValueType.INTEGER),
                        new Effect(new CardTarget(ResourceTarget.FOOD, true), 2.0, ValueType.PERCENTAGE)
                ])
        def state = GameState.builder().hand([card]).build()
        khPersistenceService.save(dummyEngine(entity, state))

        when:
        def result = khPersistenceService.reconstructState(entity.id)

        then:
        result.isPresent()
        def restoredCard = result.get().getHand()[0]
        restoredCard.name() == "Engine Builder"
        restoredCard.effects().size() == 2
        restoredCard.effects()[0].target() == ResourceTarget.PRODUCTION
        restoredCard.effects()[1].target() == new CardTarget(ResourceTarget.FOOD, true)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private GameEngine dummyEngine(def entity, GameState state) {
        def player = entity.players[0].toPlayer()
        return new GameEngine.Builder()
                .withId(entity.id)
                .withPlayer(player)
                .withState(state)
                .build()
    }
}
