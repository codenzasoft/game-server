package com.codenzasoft.games.spring.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.codenzasoft.games.spring.TestDatabaseConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
@Transactional
class GamesRepositoryTest {

  @Autowired GamesRepository gamesRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM xo_game_moves");
    jdbcTemplate.execute("DELETE FROM xo_game_pieces");
    jdbcTemplate.execute("DELETE FROM game_players");
    jdbcTemplate.execute("DELETE FROM games");
  }

  @Test
  void findCompletedGameIds_returnsEmpty_whenNoGames() {
    assertThat(gamesRepository.findCompletedGameIds()).isEmpty();
  }
}
