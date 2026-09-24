package com.codenzasoft.games.spring.services.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wraps a bot {@link HeartsPlayerObserver} and also acts as a {@link HeartsListener} to capture the
 * full decision context (round state, legal cards, card played) into {@link BotDecisionLogService}.
 *
 * <p>Threading: both {@code yourTurnToPlay} and {@code cardPlayed} run on the game's single-thread
 * notification executor, in that order, so the pending/complete correlation is race-free.
 */
public class LoggingHeartsBotObserver implements HeartsPlayerObserver, HeartsListener {

  private final HeartsPlayerObserver delegate;
  private final UUID gameId;
  private final Player botPlayer;
  private final BotDecisionLogService logService;

  public LoggingHeartsBotObserver(
      final HeartsPlayerObserver delegate,
      final UUID gameId,
      final Player botPlayer,
      final BotDecisionLogService logService) {
    this.delegate = delegate;
    this.gameId = gameId;
    this.botPlayer = botPlayer;
    this.logService = logService;
  }

  @Override
  public void yourTurnToPlay(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final Set<Card> legalCards) {
    logService.beforePlay(gameId, state, player, legalCards);
    delegate.yourTurnToPlay(game, state, player, legalCards);
  }

  @Override
  public void yourTurnToPass(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> hand) {
    delegate.yourTurnToPass(game, state, player, hand);
  }

  @Override
  public void cardPlayed(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final Card card,
      final int cardNumber) {
    if (player.name().equals(botPlayer.name())) {
      logService.afterCardPlayed(gameId, player, card);
    }
  }

  @Override
  public void gameStarted(final HeartsGame game, final HeartsState state) {}

  @Override
  public void gameOver(final HeartsGame game, final HeartsState state) {}

  @Override
  public void gameQuit(final HeartsGame game, final HeartsState state) {}

  @Override
  public void roundStarted(final HeartsGame game, final HeartsState state) {}

  @Override
  public void roundEnded(final HeartsGame game, final HeartsState state) {}

  @Override
  public void trickStarted(final HeartsGame game, final HeartsState state) {}

  @Override
  public void trickEnded(final HeartsGame game, final HeartsState state) {}

  @Override
  public void passSelected(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final List<Card> cards) {}

  @Override
  public void passComplete(final HeartsGame game, final HeartsState state) {}
}
