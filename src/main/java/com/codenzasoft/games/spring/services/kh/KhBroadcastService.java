package com.codenzasoft.games.spring.services.kh;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.KhObserver;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.messages.KhContext;
import com.codenzasoft.games.spring.messages.KhMessage;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.views.kh.KhMatchView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Pushes King's Hand match state to clients over websockets. Registered on each {@link GameEngine}
 * as a {@link KhObserver}, so state pushes are driven by the engine's lifecycle/update events
 * rather than the controller. Each human seat receives its own {@link KhMessage} carrying the full
 * match {@link GameState} plus a {@link KhContext} that tells the client which seat it occupies;
 * bot seats are skipped (they have no client).
 */
@Service
public class KhBroadcastService implements KhObserver {
  private static final Logger log = LoggerFactory.getLogger(KhBroadcastService.class);

  @Autowired private UsersRepository usersRepository;
  @Autowired private SimpMessagingTemplate messagingTemplate;

  // ----- KhObserver: engine-driven pushes -----

  @Override
  public void gameStarted(final GameEngine game, final GameState state) {
    broadcast(game);
  }

  @Override
  public void gameUpdated(final GameEngine game, final GameState state) {
    broadcast(game);
  }

  @Override
  public void gameOver(final GameEngine game, final GameState state) {
    broadcast(game);
  }

  // ----- Controller-triggered pushes -----

  /**
   * Broadcasts the current state to every human seat. Used by the {@code load} handler (a pull) and
   * as the shared implementation behind the observer callbacks.
   *
   * @param game The {@link GameEngine} whose state should be broadcast.
   */
  public void gameLoaded(final GameEngine game) {
    broadcast(game);
  }

  /** Sends the current match state to each human player, tagged with that player's seat. */
  private void broadcast(final GameEngine game) {
    final List<Player> seats = game.getPlayers();
    if (seats.isEmpty()) {
      log.warn("broadcast: no players on game {}", game.getId());
      return;
    }
    final GameState state = game.getState();
    for (int seat = 0; seat < seats.size(); seat++) {
      if (game.isBotSeat(seat)) {
        continue; // bots have no client to receive state
      }
      final UserEntity userEntity = usersRepository.findById(seats.get(seat).id()).orElse(null);
      if (userEntity == null) continue;
      final String storedUsername = userEntity.getUsername();
      final String displayName = userEntity.getDisplayName();
      final KhContext context = new KhContext(displayName, game.getId(), seat);
      // Per-seat view: the recipient's own kingdom in full, opponents redacted (hidden info).
      final KhMessage message = new KhMessage(context, KhMatchView.forSeat(state, seat));
      messagingTemplate.convertAndSendToUser(
          storedUsername, "/queue/games/" + game.getId(), message);
      log.debug("Sent game state to user '{}' (seat {})", storedUsername, seat);
    }
  }

  /**
   * Notifies a player that their game has been quit. Sends a simple quit acknowledgement to the
   * player's user-specific queue at {@code /user/queue/games/{gameId}}.
   *
   * @param gameId The UUID of the game that was quit.
   * @param username The username of the player to notify.
   */
  public void gameQuit(final UUID gameId, final String username) {
    messagingTemplate.convertAndSendToUser(
        username, "/queue/games/" + gameId, Map.of("quit", true));
    log.debug("Sent quit acknowledgement to user '{}'", username);
  }

  /**
   * Sends the pending-match waiting room to each seated human: a lightweight {@code {lobby: …}}
   * message (no {@link GameState} exists yet) carrying the current roster so the client can show a
   * "waiting for a friend to join" overlay with the invite link. Mirrors Hearts' lobby broadcast.
   *
   * @param gameId The pending game.
   * @param players The seated users so far (the host, until a second human joins).
   */
  public void broadcastLobby(final UUID gameId, final List<UserEntity> players) {
    final List<String> names = players.stream().map(UserEntity::getDisplayName).toList();
    final Map<String, Object> payload =
        Map.of("lobby", Map.of("gameId", gameId.toString(), "players", names));
    for (final UserEntity player : players) {
      messagingTemplate.convertAndSendToUser(
          player.getUsername(), "/queue/games/" + gameId, payload);
    }
    log.debug("Sent KH lobby to {} player(s) of game {}", players.size(), gameId);
  }
}
