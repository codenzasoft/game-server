package com.codenzasoft.games.spring.services.hearts;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.*;
import com.codenzasoft.games.hearts.Pass;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.messages.HeartsContext;
import com.codenzasoft.games.spring.messages.HeartsMessage;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Sends game state to players via websockets. The {@link HeartsBroadcastService} is added to each
 * {@link com.codenzasoft.games.hearts.HeartsGame} as a {@link
 * com.codenzasoft.games.hearts.HeartsListener} and sends messages to players for each state change.
 */
@Service
public class HeartsBroadcastService implements HeartsListener {
  private static final Logger log = LoggerFactory.getLogger(HeartsBroadcastService.class);

  @Autowired private GamesUserDetailsService gamesUserDetailsService;
  @Autowired private SimpMessagingTemplate messagingTemplate;

  /**
   * Sends the provided game state to all (non-bot) users playing the game. This results in a STOMP
   * websocket message being sent to each player that has registered for game specific messages. A
   * user specific context and state is created for each user.
   *
   * @param game The {@link HeartsGame} the state describes.
   * @param state The {@link HeartsState} of the game.
   * @param nextGameId The id of the next game to play if the current game is over and a new game
   *     was requested, or <code>null</code> if none.
   */
  private void notifyUsers(final HeartsGame game, final HeartsState state, final UUID nextGameId) {
    final List<UserEntity> users = getNonBots(game);
    for (final UserEntity user : users) {
      final HeartsContext context = buildContext(user, game, state, nextGameId);
      final HeartsMessage message = new HeartsMessage(context, state);
      messagingTemplate.convertAndSendToUser(
          user.getUsername(), "/queue/games/" + context.gameId(), message);
      log.debug("attempted to send message to user {}", user.getUsername());
    }
  }

  public HeartsContext buildContext(
      final UserEntity user,
      final HeartsGame game,
      final HeartsState state,
      final UUID nextGameId) {
    final Player player = user.toPlayer();
    final List<Card> hand = state.getHand(player);
    final Optional<String> nextPlayer = state.getNextPlayer();
    final List<Card> validCards = new ArrayList<>();
    if (nextPlayer.isPresent() && nextPlayer.get().equals(player.name())) {
      validCards.addAll(game.getValidCards(player));
    }
    final boolean passingRequired =
        state
            .getCurrentRound()
            .map(
                r ->
                    r.pass() != Pass.KEEPER
                        && !r.isPassComplete()
                        && !r.passes().containsKey(player.name()))
            .orElse(false);
    return new HeartsContext(
        player.name(), game.getId(), hand, validCards, passingRequired, nextGameId);
  }

  private List<UserEntity> getNonBots(final Game<?> game) {
    return game.getPlayers().stream()
        .map(player -> gamesUserDetailsService.getUserByUsernameOrEmail(player.name()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(user -> !user.isBot())
        .toList();
  }

  /**
   * Sends a lobby-phase update to all current players, letting the frontend know the game has not
   * yet started and who has joined so far.
   *
   * @param gameId The game ID.
   * @param players The players currently in the lobby (human only; bots are not in lobby phase).
   */
  public void broadcastLobby(final UUID gameId, final List<UserEntity> players) {
    final List<String> names = players.stream().map(UserEntity::getUsername).toList();
    final Map<String, Object> payload =
        Map.of("lobby", Map.of("gameId", gameId.toString(), "players", names));
    for (final UserEntity player : players) {
      messagingTemplate.convertAndSendToUser(
          player.getUsername(), "/queue/games/" + gameId, payload);
    }
  }

  @Override
  public void gameStarted(final HeartsGame game, final HeartsState state) {
    notifyUsers(game, state, null);
  }

  @Override
  public void gameOver(final HeartsGame game, final HeartsState state) {
    notifyUsers(game, state, null);
  }

  @Override
  public void gameQuit(final HeartsGame game, final HeartsState state) {
    notifyUsers(game, state, null);
  }

  @Override
  public void roundStarted(final HeartsGame game, final HeartsState state) {
    notifyUsers(game, state, null);
  }

  @Override
  public void roundEnded(final HeartsGame game, final HeartsState state) {
    notifyUsers(game, state, null);
  }

  @Override
  public void trickStarted(final HeartsGame game, final HeartsState state) {
    notifyUsers(game, state, null);
  }

  @Override
  public void trickEnded(final HeartsGame game, final HeartsState state) {
    notifyUsers(game, state, null);
  }

  @Override
  public void cardPlayed(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final Card card,
      final int cardNumber) {
    notifyUsers(game, state, null);
  }

  @Override
  public void passSelected(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> cards) {
    notifyUsers(game, state, null);
  }

  @Override
  public void passComplete(final HeartsGame game, final HeartsState state) {
    notifyUsers(game, state, null);
  }
}
