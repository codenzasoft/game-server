package com.codenzasoft.games.spring.services.xo;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.messages.XoContext;
import com.codenzasoft.games.spring.messages.XoMessage;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.xo.XoGame;
import com.codenzasoft.games.xo.XoGameListener;
import com.codenzasoft.games.xo.XoState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Sends game state to players via websockets. The {@link XoBroadcastService} is added to each
 * {@link XoGame} as a {@link XoGameListener} and sends messages to players for each state change.
 */
@Service
public class XoBroadcastService implements XoGameListener {
  private static final Logger log = LoggerFactory.getLogger(XoBroadcastService.class);

  @Autowired private GamesUserDetailsService gamesUserDetailsService;
  @Autowired private SimpMessagingTemplate messagingTemplate;

  @Override
  public void gameStarted(XoGame game, XoState state) {
    notifyUsers(game, state, null);
  }

  @Override
  public void gameChanged(XoGame game, XoState state) {
    notifyUsers(game, state, null);
  }

  @Override
  public void gameOver(XoGame game, XoState state) {
    notifyUsers(game, state, null);
  }

  @Override
  public void gameQuit(XoGame game, XoState state) {
    notifyUsers(game, state, null);
  }

  public void playAgain(final XoGame game, final XoState state, final UUID nextGameId) {
    notifyUsers(game, state, nextGameId);
  }

  public void broadcastLobby(final UUID gameId, final List<UserEntity> players) {
    final List<String> playerNames = players.stream().map(UserEntity::getUsername).toList();
    for (final UserEntity player : players) {
      if (!player.isBot()) {
        messagingTemplate.convertAndSendToUser(
            player.getUsername(),
            "/queue/games/" + gameId,
            Map.of("lobby", Map.of("gameId", gameId.toString(), "players", playerNames)));
        log.debug("sent lobby update to {} for game {}", player.getUsername(), gameId);
      }
    }
  }

  /**
   * Sends the provided game state to all (non-bot) users playing the game. This results in a STOMP
   * websocket message being sent to each player that has registered for game specific messages. A
   * user specific context is created for each user such that the client (user interface) can
   * determine which {@link com.codenzasoft.games.xo.Piece} they are playing.
   *
   * @param game The {@link XoGame} the state describes.
   * @param state The {@link XoState} of the game.
   * @param nextGameId The id of the next game to play if the current game is over and a new game
   *     was requested, or <code>null</code> if none.
   */
  private void notifyUsers(final XoGame game, final XoState state, final UUID nextGameId) {
    final List<UserEntity> users = getNonBots(game);
    for (final UserEntity user : users) {
      final XoContext context = buildContext(user, game, nextGameId);
      final XoMessage message = new XoMessage(context, state);
      messagingTemplate.convertAndSendToUser(
          user.getUsername(), "/queue/games/" + context.gameId(), message);
      log.debug("attempted to send message to user {}", user.getUsername());
    }
  }

  public XoContext buildContext(final UserEntity user, final XoGame game, final UUID nextGameId) {
    final Player player = user.toPlayer();
    return new XoContext(player.name(), game.getId(), nextGameId);
  }

  private List<UserEntity> getNonBots(final Game<?> game) {
    return game.getPlayers().stream()
        .map(player -> gamesUserDetailsService.getUserByUsernameOrEmail(player.name()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(user -> !user.isBot())
        .toList();
  }
}
