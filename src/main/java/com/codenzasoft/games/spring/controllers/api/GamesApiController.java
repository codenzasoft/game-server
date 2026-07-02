package com.codenzasoft.games.spring.controllers.api;

import com.codenzasoft.games.spring.entities.GameEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.hearts.HeartsGameEntity;
import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games")
public class GamesApiController {
  private static final Logger log = LoggerFactory.getLogger(GamesApiController.class);

  @Autowired private GameService gameService;
  @Autowired private GamesUserDetailsService gamesUserDetailsService;

  /** Returns all active (not over, not quit) games the authenticated user is a player in. */
  @GetMapping
  public ResponseEntity<?> listGames(final Principal principal) {
    return withUser(
        principal,
        user -> {
          final List<GameEntity> games = gameService.getActiveGames(user);
          return ResponseEntity.ok(games);
        });
  }

  /** Creates a new Hearts game against three bots and returns the game id. */
  @PostMapping("/hearts")
  public ResponseEntity<?> createHeartsGame(final Principal principal) {
    return withUser(
        principal,
        user -> {
          try {
            final HeartsGameEntity game = gameService.createHeartsGame(user);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", game.getId().toString()));
          } catch (IllegalStateException e) {
            log.warn(
                "Hearts game creation rejected for {}: {}", user.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
          }
        });
  }

  /**
   * Creates a pending multiplayer Hearts game (lobby) and returns the game id. Other players join
   * by connecting to the game's WebSocket and sending a load message.
   */
  @PostMapping("/hearts/multiplayer")
  public ResponseEntity<?> createMultiplayerHeartsGame(final Principal principal) {
    return withUser(
        principal,
        user -> {
          try {
            final HeartsGameEntity game = gameService.createPendingHeartsGame(user);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", game.getId().toString()));
          } catch (IllegalStateException e) {
            log.warn(
                "Pending Hearts game creation rejected for {}: {}",
                user.getUsername(),
                e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
          }
        });
  }

  /** Quits (deletes) the identified game on behalf of the authenticated user. */
  @DeleteMapping("/{gameId}")
  public ResponseEntity<?> deleteGame(@PathVariable final UUID gameId, final Principal principal) {
    return withUser(
        principal,
        user -> {
          try {
            gameService.quitGame(gameId, user);
            return ResponseEntity.noContent().build();
          } catch (IllegalStateException e) {
            log.warn("Game deletion rejected for {}: {}", user.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage()));
          }
        });
  }

  private ResponseEntity<?> withUser(
      final Principal principal,
      final java.util.function.Function<UserEntity, ResponseEntity<?>> action) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
    }
    return gamesUserDetailsService
        .getUserByUsernameOrEmail(principal.getName())
        .map(action)
        .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }
}
