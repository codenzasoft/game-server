package com.codenzasoft.games.spring.controllers.ws;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.messages.XoPlayMessage;
import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.services.xo.XoBroadcastService;
import com.codenzasoft.games.xo.XoGame;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class XoController {
  private static final Logger log = LoggerFactory.getLogger(XoController.class);

  @Autowired private GameService gameService;
  @Autowired private GamesUserDetailsService gamesUserDetailsService;
  @Autowired private XoBroadcastService xoBroadcastService;

  @MessageMapping("/xo/{gameId}/load")
  public void load(@DestinationVariable String gameId) {
    gameService
        .getOrBuildXoGame(UUID.fromString(gameId))
        .ifPresent(game -> xoBroadcastService.gameStarted(game, game.buildState()));
  }

  @MessageMapping("/xo/{gameId}/play")
  public void play(
      @DestinationVariable String gameId, @Payload XoPlayMessage message, Principal principal) {
    final Optional<XoGame> game = gameService.getOrBuildXoGame(UUID.fromString(gameId));
    final Optional<UserEntity> userEntity = findUser(principal);
    if (game.isEmpty() || userEntity.isEmpty()) {
      return;
    }
    game.get().playPiece(userEntity.get().toPlayer(), message.row(), message.column());
  }

  @MessageMapping("/xo/{gameId}/quit")
  public void quit(@DestinationVariable String gameId, Principal principal) {
    final Optional<XoGame> game = gameService.getOrBuildXoGame(UUID.fromString(gameId));
    final Optional<UserEntity> userEntity = findUser(principal);
    if (game.isEmpty() || userEntity.isEmpty()) {
      return;
    }
    game.get().quit(userEntity.get().toPlayer());
  }

  @MessageMapping("/xo/{gameId}/again")
  public void again(@DestinationVariable String gameId) {
    final Optional<XoGame> optGame = gameService.getOrBuildXoGame(UUID.fromString(gameId));
    if (optGame.isEmpty()) {
      return;
    }
    final XoGame game = optGame.get();
    if (!game.isOverOrQuit()) {
      log.warn("again: game {} is not over or quit", gameId);
      return;
    }
    final UUID nextGameId = UUID.randomUUID();
    gameService.createNextXoGame(nextGameId, game);
    xoBroadcastService.playAgain(game, game.buildState(), nextGameId.toString());
  }

  private Optional<UserEntity> findUser(final Principal principal) {
    return gamesUserDetailsService.getUserByUsernameOrEmail(principal.getName());
  }
}
