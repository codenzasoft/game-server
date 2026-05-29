package com.codenzasoft.games.spring.controllers.ws;

import com.codenzasoft.games.kingshand.Card;
import com.codenzasoft.games.kingshand.Event;
import com.codenzasoft.games.kingshand.EventOption;
import com.codenzasoft.games.kingshand.GameEngine;
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
    gameService.getOrBuildKhGame(UUID.fromString(gameId)).ifPresent(khBroadcastService::gameLoaded);
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

  /**
   * Applies the chosen {@link EventOption} (by zero-based index) to the current game state, then
   * draws the player's hand to start the {@link Phase#PLAY} phase.
   */
  @MessageMapping("/kh/{gameId}/event/pick")
  public void eventPick(@DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              final Event event = game.getState().getCurrentEvent();
              if (event == null) {
                log.warn("eventPick: no current event on game {}", gameId);
                return;
              }
              final int index = ((Number) body.get("index")).intValue();
              final List<EventOption> options = event.options();
              if (index < 0 || index >= options.size()) {
                log.warn("eventPick: invalid index {} (options size {})", index, options.size());
                return;
              }
              try {
                game.applyEventOption(options.get(index));
                khBroadcastService.gameLoaded(game);
              } catch (IllegalStateException e) {
                log.warn("eventPick rejected for game {}: {}", gameId, e.getMessage());
              }
            });
  }

  /**
   * Skips the RANDOM_EVENT phase when no eligible event was rolled (i.e. {@code currentEvent} is
   * {@code null}). Delegates to {@link GameEngine#advancePhase()}, which transitions to {@link
   * Phase#PLAY} and draws the player's hand in one step.
   */
  @MessageMapping("/kh/{gameId}/event/skip")
  public void eventSkip(@DestinationVariable String gameId) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              try {
                game.advancePhase();
                khBroadcastService.gameLoaded(game);
              } catch (IllegalStateException e) {
                log.warn("eventSkip rejected for game {}: {}", gameId, e.getMessage());
              }
            });
  }

  // ── PLAY phase actions ────────────────────────────────────────────────────

  /**
   * Places the card at hand {@code index} onto the current season's table. Deducts the production
   * cost. If a matching card already sits on the table, amplifies it (upgrade) instead of adding a
   * new slot.
   */
  @MessageMapping("/kh/{gameId}/play/card")
  public void playCard(@DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              final int index = ((Number) body.get("index")).intValue();
              final List<Card> hand = game.getState().getHand();
              if (index < 0 || index >= hand.size()) {
                log.warn("playCard: invalid index {} (hand size {})", index, hand.size());
                return;
              }
              try {
                game.placeCard(hand.get(index));
                khBroadcastService.gameLoaded(game);
              } catch (IllegalStateException e) {
                log.warn("playCard rejected for game {}: {}", gameId, e.getMessage());
              }
            });
  }

  /**
   * Demolishes the card at {@code slot} on the current season's table, charging {@link
   * GameEngine#DEMOLISH_COST_RATE} × the card's production requirement.
   */
  @MessageMapping("/kh/{gameId}/play/demolish")
  public void demolishCard(@DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              final int slot = ((Number) body.get("slot")).intValue();
              try {
                game.demolishCard(slot);
                khBroadcastService.gameLoaded(game);
              } catch (IllegalStateException e) {
                log.warn("demolishCard rejected for game {}: {}", gameId, e.getMessage());
              }
            });
  }

  /**
   * Swaps the cards at {@code fromSlot} and {@code toSlot} on the current season's table, letting
   * the player reorder their table before resolving. Because {@link GameEngine#endAndRun()} fires
   * cards left-to-right, slot order is strategically meaningful.
   *
   * <p>The request payload must contain integer fields {@code fromSlot} and {@code toSlot}. Invalid
   * indices or a non-PLAY phase are silently rejected (warn-logged) so a stale client message
   * cannot crash the session.
   */
  @MessageMapping("/kh/{gameId}/play/reorder")
  public void reorderCard(@DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              final int fromSlot = ((Number) body.get("fromSlot")).intValue();
              final int toSlot = ((Number) body.get("toSlot")).intValue();
              try {
                game.reorderCard(fromSlot, toSlot);
                khBroadcastService.gameLoaded(game);
              } catch (IllegalStateException e) {
                log.warn("reorderCard rejected for game {}: {}", gameId, e.getMessage());
              }
            });
  }

  /**
   * Ends the PLAY phase: resolves table cards left-to-right, consumes seasonal food, then advances
   * to the next ADD phase (seeding card choices). {@link GameEngine#advancePhase()} is a no-op if
   * the game has ended due to starvation, so the game-over state is broadcast cleanly.
   */
  @MessageMapping("/kh/{gameId}/play/end")
  public void endAndRun(@DestinationVariable String gameId) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              try {
                game.endAndRun();
                khBroadcastService.gameLoaded(game);
              } catch (IllegalStateException e) {
                log.warn("endAndRun rejected for game {}: {}", gameId, e.getMessage());
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
