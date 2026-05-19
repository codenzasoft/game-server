package com.codenzasoft.games.spring.controllers.ws;

import com.codenzasoft.games.kingshand.Card;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.Phase;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.services.kh.KhBroadcastService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class KhController {
  private static final Logger log = LoggerFactory.getLogger(KhController.class);

  @Autowired private GameService gameService;
  @Autowired private GamesUserDetailsService gamesUserDetailsService;
  @Autowired private KhBroadcastService khBroadcastService;
  @Autowired private SimpMessagingTemplate messagingTemplate;

  /**
   * Loads the current game state and sends it to the player. For a fresh game in the ADD phase with
   * no choices offered yet, seeds the initial offer before broadcasting so the client receives a
   * fully-populated state on first load.
   */
  @MessageMapping("/kh/{gameId}/load")
  public void load(@DestinationVariable String gameId) {
    final Optional<GameEngine> game = gameService.getOrBuildKhGame(UUID.fromString(gameId));
    game.ifPresent(
        g -> {
          if (g.getState().getCurPhase() == Phase.ADD && g.getState().getAddChoices().isEmpty()) {
            g.offerAddChoices();
          }
          khBroadcastService.gameLoaded(g);
        });
  }

  /**
   * Rerolls the ADD-phase card offer. The first reroll after the initial free offer costs {@link
   * GameEngine#REROLL_COST_STEP} production; cost increases by the same step on each subsequent
   * reroll. The server rejects the request if the player cannot afford it.
   */
  @MessageMapping("/kh/{gameId}/add/offer")
  public void addOffer(@DestinationVariable String gameId) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              try {
                game.offerAddChoices();
                khBroadcastService.gameLoaded(game);
              } catch (IllegalStateException e) {
                log.warn("addOffer rejected for game {}: {}", gameId, e.getMessage());
              }
            });
  }

  /**
   * Picks one of the offered ADD-phase cards by zero-based index into {@code state.addChoices}.
   * Shuffles the chosen card into the season's draw pile and advances to {@link
   * Phase#RANDOM_EVENT}.
   */
  @MessageMapping("/kh/{gameId}/add/pick")
  public void addPick(@DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              final int index = ((Number) body.get("index")).intValue();
              final List<Card> choices = game.getState().getAddChoices();
              if (index < 0 || index >= choices.size()) {
                log.warn("addPick: invalid index {} (choices size {})", index, choices.size());
                return;
              }
              try {
                game.pickCard(choices.get(index));
                khBroadcastService.gameLoaded(game);
              } catch (IllegalStateException e) {
                log.warn("addPick rejected for game {}: {}", gameId, e.getMessage());
              }
            });
  }

  /**
   * Skips the ADD phase without picking a card. Advances the phase to {@link Phase#RANDOM_EVENT}.
   */
  @MessageMapping("/kh/{gameId}/add/skip")
  public void addSkip(@DestinationVariable String gameId) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              try {
                game.skipAdd();
                khBroadcastService.gameLoaded(game);
              } catch (IllegalStateException e) {
                log.warn("addSkip rejected for game {}: {}", gameId, e.getMessage());
              }
            });
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
