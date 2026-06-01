package com.codenzasoft.games.spring.services.kh;

import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.kh.KhGameResultEntity;
import com.codenzasoft.games.spring.repositories.GamesRepository;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.repositories.kh.KhGameResultRepository;
import jakarta.transaction.Transactional;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Persists the outcome of a King's Hand game when the player's population reaches zero. Records the
 * final year, season, and peak population, and marks the game entity as over.
 */
@Service
public class KhGamePersistenceService {
  private static final Logger log = LoggerFactory.getLogger(KhGamePersistenceService.class);

  @Autowired private KhGameResultRepository khGameResultRepository;
  @Autowired private GamesRepository gamesRepository;
  @Autowired private UsersRepository usersRepository;

  /**
   * Saves leaderboard data for a completed game. Safe to call multiple times — a second call for
   * the same game is a no-op because the result is keyed by game ID.
   *
   * @param game The {@link GameEngine} whose state should be persisted.
   */
  @Transactional
  public void saveResult(final GameEngine game) {
    final GameState state = game.getState();
    if (!state.isGameOver()) {
      return;
    }

    // Idempotency guard — don't double-write
    if (khGameResultRepository.existsById(game.getId())) {
      return;
    }

    if (game.getPlayers().isEmpty()) {
      log.warn("saveResult: no players on game {}", game.getId());
      return;
    }

    final String playerName = game.getPlayers().get(0).name();
    final UserEntity user = usersRepository.findByUserName(playerName);
    if (user == null) {
      log.warn("saveResult: could not find user for player '{}'", playerName);
      return;
    }

    // Mark game as over in the games table
    gamesRepository
        .findById(game.getId())
        .ifPresent(
            entity -> {
              if (!entity.isComplete()) {
                entity.setOverAt(new Date());
                gamesRepository.save(entity);
              }
            });

    final KhGameResultEntity result = new KhGameResultEntity();
    result.setGameId(game.getId());
    result.setUser(user);
    result.setYearSurvived(state.getYear());
    result.setSeasonSurvived(state.getCurSeason());
    result.setPeakPopulation(state.getPeakPopulation());
    khGameResultRepository.save(result);

    log.info(
        "Saved KH result for user '{}': year={} season={} peakPop={}",
        playerName,
        state.getYear(),
        state.getCurSeason(),
        state.getPeakPopulation());
  }
}
