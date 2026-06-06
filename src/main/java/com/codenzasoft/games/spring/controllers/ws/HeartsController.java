package com.codenzasoft.games.spring.controllers.ws;

import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.hearts.HeartsBroadcastService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class HeartsController {
  private static final Logger log = LoggerFactory.getLogger(HeartsController.class);

  @Autowired private GameService gameService;
  @Autowired private HeartsBroadcastService heartsBroadcastService;

  @MessageMapping("/hearts/{gameId}/load")
  public void load(@DestinationVariable String gameId) {
    final UUID id = UUID.fromString(gameId);
    gameService
        .getOrBuildHeartsGame(id)
        .ifPresentOrElse(
            game -> heartsBroadcastService.gameStarted(game, game.getGameState()),
            () -> log.warn("load: hearts game {} not found", id));
  }
}
