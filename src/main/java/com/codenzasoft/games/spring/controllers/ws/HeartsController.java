package com.codenzasoft.games.spring.controllers.ws;

import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.messages.HeartsPassMessage;
import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.services.hearts.HeartsBroadcastService;
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
public class HeartsController {
  private static final Logger log = LoggerFactory.getLogger(HeartsController.class);

  @Autowired private GameService gameService;
  @Autowired private GamesUserDetailsService gamesUserDetailsService;
  @Autowired private HeartsBroadcastService heartsBroadcastService;

  @MessageMapping("/hearts/{gameId}/load")
  public void load(@DestinationVariable String gameId) {
    final UUID id = UUID.fromString(gameId);
    gameService
        .getOrBuildHeartsGame(id)
        .ifPresentOrElse(
            game -> {
              if (game.getGameState().getCurrentRound().isEmpty()) {
                game.startNewRound();
              } else {
                heartsBroadcastService.gameStarted(game, game.getGameState());
              }
            },
            () -> log.warn("load: hearts game {} not found", id));
  }

  @MessageMapping("/hearts/{gameId}/pass")
  public void pass(
      @DestinationVariable String gameId, @Payload HeartsPassMessage message, Principal principal) {
    final Optional<HeartsGame> game = gameService.getOrBuildHeartsGame(UUID.fromString(gameId));
    final Optional<UserEntity> user = findUser(principal);
    if (game.isEmpty() || user.isEmpty()) return;
    game.get().setPass(user.get().toPlayer(), message.cards());
  }

  @MessageMapping("/hearts/{gameId}/play")
  public void play(@DestinationVariable String gameId, @Payload Card card, Principal principal) {
    final Optional<HeartsGame> game = gameService.getOrBuildHeartsGame(UUID.fromString(gameId));
    final Optional<UserEntity> user = findUser(principal);
    if (game.isEmpty() || user.isEmpty()) return;
    game.get().playCard(user.get().toPlayer(), card);
  }

  @MessageMapping("/hearts/{gameId}/quit")
  public void quit(@DestinationVariable String gameId, Principal principal) {
    final UUID id = UUID.fromString(gameId);
    final Optional<HeartsGame> game = gameService.getOrBuildHeartsGame(id);
    final Optional<UserEntity> user = findUser(principal);
    if (game.isEmpty() || user.isEmpty()) return;
    game.get().quit();
    try {
      gameService.quitGame(id, user.get());
    } catch (IllegalStateException e) {
      log.warn("quit: {}", e.getMessage());
    }
  }

  private Optional<UserEntity> findUser(final Principal principal) {
    return gamesUserDetailsService.getUserByUsernameOrEmail(principal.getName());
  }
}
