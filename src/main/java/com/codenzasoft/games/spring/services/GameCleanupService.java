package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.spring.repositories.GamesRepository;
import com.codenzasoft.games.spring.services.hearts.BotDecisionLogService;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class GameCleanupService {
  private static final Logger log = LoggerFactory.getLogger(GameCleanupService.class);

  @Autowired private GamesRepository gamesRepository;
  @Autowired private GameCache gameCache;
  @Autowired private BotDecisionLogService botDecisionLogService;

  @Scheduled(
      initialDelayString = "${game.cleanup.initialDelay:120000}",
      fixedDelayString = "${game.cleanup.fixedDelay:21600000}")
  @Transactional
  public void deleteCompletedGames() {
    final List<UUID> ids = gamesRepository.findCompletedGameIds();
    if (ids.isEmpty()) {
      return;
    }
    ids.forEach(gameCache::removeGame);
    ids.forEach(botDecisionLogService::clearGame);
    final int deleted = gamesRepository.deleteCompletedGames();
    log.info("Deleted {} completed game(s)", deleted);
  }
}
