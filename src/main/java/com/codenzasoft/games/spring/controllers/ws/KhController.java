package com.codenzasoft.games.spring.controllers.ws;

import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.services.kh.KhBroadcastService;
import com.newrelic.api.agent.NewRelic;
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
import org.springframework.stereotype.Controller;

@Controller
public class KhController {
  private static final Logger log = LoggerFactory.getLogger(KhController.class);

  @Autowired private GameService gameService;
  @Autowired private GamesUserDetailsService gamesUserDetailsService;
  @Autowired private KhBroadcastService khBroadcastService;

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
              } catch (final IllegalStateException e) {
                log.error("addOffer rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
              }
            });
  }

  /**
   * Picks one of the offered ADD-phase cards by stable card name. Shuffles the chosen card into the
   * season's draw pile and advances to the PLAY phase.
   */
  @MessageMapping("/kh/{gameId}/add/pick")
  public void addPick(@DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              final String name = (String) body.get("name");
              try {
                game.pickCardByName(name);
                khBroadcastService.gameLoaded(game);
              } catch (final IllegalStateException e) {
                log.error("addPick rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
              }
            });
  }

  /**
   * Skips the ADD phase without picking a card. Advances the phase to {@link
   * com.codenzasoft.games.kingshand.Phase#PLAY}.
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
              } catch (final IllegalStateException e) {
                log.error("addSkip rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
              }
            });
  }

  // ── COUNCIL_ADD / COUNCIL_MEETING phase actions ─────────────────────────────

  /**
   * Hires an advisor offered this COUNCIL_ADD round by their stable catalog id, deducting their
   * hiring cost in gold and seating them on the council. Advances the phase to {@link
   * com.codenzasoft.games.kingshand.Phase#COUNCIL_MEETING} and seeds that year's single rotating
   * project proposal.
   */
  @MessageMapping("/kh/{gameId}/council/hire")
  public void councilHire(@DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              final String id = (String) body.get("id");
              try {
                game.hireAdvisorById(id);
                khBroadcastService.gameLoaded(game);
              } catch (final IllegalStateException e) {
                log.error("councilHire rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
              }
            });
  }

  /**
   * Declines to hire anyone this year. Advances the phase to {@link
   * com.codenzasoft.games.kingshand.Phase#COUNCIL_MEETING} and seeds that year's single rotating
   * project proposal.
   */
  @MessageMapping("/kh/{gameId}/council/skip")
  public void councilSkip(@DestinationVariable String gameId) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              try {
                game.skipCouncilAdd();
                khBroadcastService.gameLoaded(game);
              } catch (final IllegalStateException e) {
                log.error("councilSkip rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
              }
            });
  }

  /**
   * Accepts the current COUNCIL_MEETING proposal (a Reform or Decree from one seated advisor),
   * applying its effect. Advances the phase to {@link
   * com.codenzasoft.games.kingshand.Phase#ADD} and seeds the next ADD-phase offer.
   */
  @MessageMapping("/kh/{gameId}/council/accept")
  public void councilAccept(@DestinationVariable String gameId) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              try {
                game.acceptCouncilProposal();
                khBroadcastService.gameLoaded(game);
              } catch (final IllegalStateException e) {
                log.error("councilAccept rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
              }
            });
  }

  /**
   * Passes on the current COUNCIL_MEETING proposal without applying it. Advances the phase to
   * {@link com.codenzasoft.games.kingshand.Phase#ADD} and seeds the next ADD-phase offer.
   */
  @MessageMapping("/kh/{gameId}/council/pass")
  public void councilPass(@DestinationVariable String gameId) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              try {
                game.passCouncilProposal();
                khBroadcastService.gameLoaded(game);
              } catch (final IllegalStateException e) {
                log.error("councilPass rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
              }
            });
  }

  // ── PLAY phase actions ────────────────────────────────────────────────────

  /**
   * Places the hand card identified by its stable card name onto the current season's table.
   * Deducts the production cost. If a matching card already sits on the table, amplifies it
   * (upgrade) instead of adding a new slot.
   */
  @MessageMapping("/kh/{gameId}/play/card")
  public void playCard(@DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              final String name = (String) body.get("name");
              try {
                game.placeCardByName(name);
                khBroadcastService.gameLoaded(game);
              } catch (final IllegalStateException e) {
                log.error("playCard rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
              }
            });
  }

  /**
   * Demolishes the table card identified by its stable card name, charging {@link
   * GameEngine#DEMOLISH_COST_RATE} × the card's production requirement.
   */
  @MessageMapping("/kh/{gameId}/play/demolish")
  public void demolishCard(@DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              final String name = (String) body.get("name");
              try {
                game.demolishCardByName(name);
                khBroadcastService.gameLoaded(game);
              } catch (final IllegalStateException e) {
                log.error("demolishCard rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
              }
            });
  }

  /**
   * Reorders the current season's table to match the provided complete list of card names. The
   * client sends the full desired order after every drag rather than individual swap operations, so
   * the server always applies the player's complete intent in one call.
   */
  @MessageMapping("/kh/{gameId}/play/reorder")
  public void reorderTable(@DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              @SuppressWarnings("unchecked")
              final List<String> names = (List<String>) body.get("names");
              try {
                game.reorderTable(names);
                khBroadcastService.gameLoaded(game);
              } catch (final IllegalStateException e) {
                log.error("reorderTable rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
              }
            });
  }

  /**
   * Commissions a Great Work by its stable name. Pays the one-time production cost, places the
   * under-construction card on the current season's table, and registers the construction state.
   * The server rejects the request if the player cannot afford it, the table is full, the season
   * doesn't match, or the Great Work is already underway.
   */
  @MessageMapping("/kh/{gameId}/play/commission")
  public void commissionGreatWork(
      @DestinationVariable String gameId, @Payload Map<String, Object> body) {
    gameService
        .getOrBuildKhGame(UUID.fromString(gameId))
        .ifPresent(
            game -> {
              final String name = (String) body.get("name");
              try {
                game.commissionGreatWork(name);
                khBroadcastService.gameLoaded(game);
              } catch (final IllegalStateException | IllegalArgumentException e) {
                log.error("commissionGreatWork rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
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
              } catch (final IllegalStateException e) {
                log.error("endAndRun rejected for game {}: {}", gameId, e.getMessage());
                NewRelic.noticeError(e);
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
    gameService.getOrBuildKhGame(id).ifPresent(GameEngine::quit);
    try {
      gameService.quitGame(id, userEntity.get());
    } catch (final IllegalStateException e) {
      log.error("Quit rejected for {}: {}", userEntity.get().getUsername(), e.getMessage());
      NewRelic.noticeError(e);
      return;
    }
    khBroadcastService.gameQuit(id, userEntity.get().getUsername());
  }

  private Optional<UserEntity> findUser(final Principal principal) {
    return gamesUserDetailsService.getUserByUsernameOrEmail(principal.getName());
  }
}
