package com.codenzasoft.games.spring.controllers.ws;

import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.services.kh.KhBroadcastService;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class KhController {
  private static final Logger log = LoggerFactory.getLogger(KhController.class);

  @Autowired private GameService gameService;
  @Autowired private GamesUserDetailsService gamesUserDetailsService;
  @Autowired private KhBroadcastService khBroadcastService;
  @Autowired private SimpMessagingTemplate messagingTemplate;

  @MessageMapping("/kh/{gameId}/load")
  public void load(@DestinationVariable String gameId) {
    final Optional<GameEngine> game = gameService.getOrBuildKhGame(UUID.fromString(gameId));
    game.ifPresent(khBroadcastService::gameLoaded);
  }

  @MessageMapping("/kh/{gameId}/quit")
  public void quit(@DestinationVariable String gameId, Principal principal) {
    final Optional<UserEntity> userEntity = findUser(principal);
    if (userEntity.isEmpty()) {
      return;
    }
    final UUID id = UUID.fromString(gameId);
    try {
      gameService.quitGame(id, userEntity.get());
    } catch (IllegalStateException e) {
      log.warn("Quit rejected for {}: {}", userEntity.get().getUsername(), e.getMessage());
      return;
    }
    messagingTemplate.convertAndSendToUser(
        userEntity.get().getUsername(), "/queue/games/" + gameId, Map.of("quit", true));
    log.debug("Quit game {} for user {}", gameId, userEntity.get().getUsername());
  }

  private Optional<UserEntity> findUser(final Principal principal) {
    return gamesUserDetailsService.getUserByUsernameOrEmail(principal.getName());
  }
}
