package com.codenzasoft.games.spring.repositories

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GamesRepositorySpec extends Specification {

    @Autowired GamesRepository gamesRepository
    @Autowired JdbcTemplate jdbcTemplate

    def setup() {
        jdbcTemplate.execute("DELETE FROM xo_game_moves")
        jdbcTemplate.execute("DELETE FROM xo_game_pieces")
        jdbcTemplate.execute("DELETE FROM game_players")
        jdbcTemplate.execute("DELETE FROM games")
    }

    def "findCompletedGameIds returns empty when no games exist"() {
        expect:
        gamesRepository.findCompletedGameIds().isEmpty()
    }
}
