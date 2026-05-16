package com.codenzasoft.games.spring.services.kh;

import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.messages.KhContext;
import com.codenzasoft.games.spring.messages.KhMessage;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Sends game state to the player via websockets. The {@link KhBroadcastService} broadcasts a {@link
 * KhMessage} containing a player-specific {@link KhContext} and the current {@link
 * com.codenzasoft.games.kingshand.GameState} to the registered client.
 */
@Service
public class KhBroadcastService {
  private static final Logger log = LoggerFactory.getLogger(KhBroadcastService.class);

  @Autowired private GamesUserDetailsService gamesUserDetailsService;
  @Autowired private SimpMessagingTemplate messagingTemplate;

  /**
   * Broadcasts the current game state to the player. Sends a {@link KhMessage} to the player's
   * user-specific queue at {@code /user/queue/games/{gameId}}.
   *
   * @param game The {@link GameEngine} whose state should be broadcast.
   */
  public void gameLoaded(final GameEngine game) {
    if (game.getPlayers().isEmpty()) {
      log.warn("gameLoaded: no players on game {}", game.getId());
      return;
    }
    final String playerName = game.getPlayers().get(0).name();
    final Optional<UserEntity> userOpt =
        gamesUserDetailsService.getUserByUsernameOrEmail(playerName);
    if (userOpt.isEmpty()) {
      log.warn("gameLoaded: could not find user for player '{}'", playerName);
      return;
    }
    final UserEntity user = userOpt.get();
    final KhContext context = new KhContext(user.getUsername(), game.getId());
    final KhMessage message = new KhMessage(context, game.getState());
    messagingTemplate.convertAndSendToUser(
        user.getUsername(), "/queue/games/" + game.getId(), message);
    log.debug("Sent game state to user '{}'", user.getUsername());
  }
}
