package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class GuestCleanupService {
  private static final Logger log = LoggerFactory.getLogger(GuestCleanupService.class);

  @Autowired private UsersRepository usersRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Scheduled(
      fixedDelayString = "${guest.cleanup.fixedDelay:3600000}",
      initialDelayString = "${guest.cleanup.initialDelay:3600000}")
  @Transactional
  public void deleteExpiredGuests() {
    final Date cutoff = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));

    final List<UserEntity> candidates =
        usersRepository.findByRolesRole(Role.GUEST).stream()
            .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().before(cutoff))
            .toList();

    if (candidates.isEmpty()) {
      return;
    }

    // Exclude guests still in an active game
    final List<UserEntity> toDelete = candidates.stream().filter(u -> !hasActiveGame(u)).toList();

    if (toDelete.isEmpty()) {
      return;
    }

    for (UserEntity guest : toDelete) {
      // Delete the guest's games first; ON DELETE CASCADE removes game_players,
      // xo_game_pieces, xo_game_moves, hearts_round_hands, hearts_round_passes,
      // and hearts_tricks, leaving no FK references before the user row is removed.
      jdbcTemplate.update(
          "DELETE FROM games WHERE id IN (SELECT game_id FROM game_players WHERE user_id = ?)",
          guest.getId());
      usersRepository.delete(guest);
    }

    log.info("Deleted {} expired guest account(s)", toDelete.size());
  }

  private boolean hasActiveGame(UserEntity user) {
    final Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_players gp "
                + "JOIN games g ON g.id = gp.game_id "
                + "WHERE gp.user_id = ? AND g.over_at IS NULL AND g.quit_at IS NULL",
            Integer.class,
            user.getId());
    return count != null && count > 0;
  }
}
