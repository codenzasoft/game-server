package com.codenzasoft.games.spring.controllers.ws;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.xo.XoGameEntity;
import com.codenzasoft.games.spring.messages.XoPlayMessage;
import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.services.xo.XoBroadcastService;
import com.codenzasoft.games.xo.XoGame;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

@Controller
public class XoController {
  private static final Logger log = LoggerFactory.getLogger(XoController.class);

  @Autowired private GameService gameService;
  @Autowired private GamesUserDetailsService gamesUserDetailsService;
  @Autowired private XoBroadcastService xoBroadcastService;

  @MessageMapping("/xo/{gameId}/load")
  public void load(@DestinationVariable String gameId, @AuthenticationPrincipal UserDetails user) {
    final Optional<XoGameEntity> gameEntity = findXoGame(gameId);
    if (gameEntity.isEmpty()) {
      return;
    }
    final XoGame game = gameService.buildTicTacToeGame(gameEntity.get());
    xoBroadcastService.gameStarted(game, game.buildState());
  }

  @MessageMapping("/xo/{gameId}/play")
  public void play(
      @DestinationVariable String gameId,
      @Payload XoPlayMessage message,
      @AuthenticationPrincipal UserDetails user) {
    final Optional<XoGameEntity> gameEntity = findXoGame(gameId);
    final Optional<UserEntity> userEntity = findUser(user);
    if (gameEntity.isEmpty() || userEntity.isEmpty()) {
      return;
    }
    final XoGame game = gameService.buildTicTacToeGame(gameEntity.get());
    game.playPiece(userEntity.get().toPlayer(), message.row(), message.col());
  }

  @MessageMapping("/xo/{gameId}/quit")
  public void quit(@DestinationVariable String gameId, @AuthenticationPrincipal UserDetails user) {
    final Optional<XoGameEntity> gameEntity = findXoGame(gameId);
    if (gameEntity.isEmpty()) {
      return;
    }
    final XoGame game = gameService.buildTicTacToeGame(gameEntity.get());
    game.quit();
  }

  @MessageMapping("/xo/{gameId}/again")
  public void again(@DestinationVariable String gameId, @AuthenticationPrincipal UserDetails user) {
    final Optional<XoGameEntity> gameEntity = findXoGame(gameId);
    if (gameEntity.isEmpty()) {
      return;
    }
    final XoGame game = gameService.buildTicTacToeGame(gameEntity.get());
    if (!game.isOverOrQuit()) {
      log.warn("again: game {} is not over or quit", gameId);
      return;
    }
    final UUID nextGameId = UUID.randomUUID();
    gameService.createNextXoGame(nextGameId, gameEntity.get());
    xoBroadcastService.playAgain(game, game.buildState(), nextGameId.toString());
  }

  private Optional<XoGameEntity> findXoGame(final String gameId) {
    try {
      return gameService.findXoGame(UUID.fromString(gameId));
    } catch (IllegalArgumentException e) {
      log.warn("invalid game id: {}", gameId);
      return Optional.empty();
    }
  }

  private Optional<UserEntity> findUser(final UserDetails user) {
    return gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
  }
}
