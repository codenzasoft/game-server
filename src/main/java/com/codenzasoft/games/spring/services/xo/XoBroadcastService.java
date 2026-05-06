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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Broadcasts {@link XoMessage}s to all registered sessions by registering as a {@link
 * XoGameListener} for each game.
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

  public void playAgain(final XoGame game, final XoState state, final String nextGameId) {
    notifyUsers(game, state, nextGameId);
  }

  private void notifyUsers(final XoGame game, final XoState state, final String nextGameId) {
    final List<UserEntity> users = getNonBots(game);
    for (final UserEntity user : users) {
      final XoContext context = buildContext(user, game, state, nextGameId);
      final XoMessage message = new XoMessage(context, state);
      // identifies the user ("self") in the state so the client knows which piece they are playing
      messagingTemplate.convertAndSendToUser(
          user.getUsername(), "/queue/games/" + context.gameId(), message);
      log.info("attempted to send message to user {}", user.getUsername());
    }
  }

  public XoContext buildContext(
      final UserEntity user, final XoGame game, final XoState state, final String nextGameId) {
    final Player player = user.toPlayer();
    return new XoContext(player.name(), game.getId().toString(), nextGameId);
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
