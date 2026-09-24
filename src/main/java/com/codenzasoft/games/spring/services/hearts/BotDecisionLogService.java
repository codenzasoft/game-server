package com.codenzasoft.games.spring.services.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Rolling in-memory log of bot strategy decisions for Hearts games. Captures the round state and
 * legal cards the bot saw before each play, correlated with the card it chose. Bounded to {@link
 * #MAX_PER_GAME} entries per game; older entries are dropped as new ones arrive.
 *
 * <p>Entries are added from the single-threaded game notification executor; {@link
 * #getDecisions(UUID)} is called from HTTP request threads, so the per-game deque is synchronized.
 */
@Service
public class BotDecisionLogService {

  static final int MAX_PER_GAME = 20;

  private final Map<UUID, Deque<BotDecision>> log = new ConcurrentHashMap<>();
  private final Map<String, PendingDecision> pending = new ConcurrentHashMap<>();

  private record PendingDecision(UUID playerUUID, RoundState roundState, Set<Card> legalCards) {}

  /** Called just before the bot's strategy runs. Stores the pre-play context. */
  public void beforePlay(
      final UUID gameId, final HeartsState state, final Player player, final Set<Card> legalCards) {
    state
        .getCurrentRound()
        .ifPresent(
            round ->
                pending.put(
                    key(gameId, player.name()),
                    new PendingDecision(player.id(), round, legalCards)));
  }

  /** Called after the bot plays a card. Completes and stores the snapshot. */
  public void afterCardPlayed(final UUID gameId, final Player player, final Card card) {
    final PendingDecision ctx = pending.remove(key(gameId, player.name()));
    if (ctx == null) return;

    final BotDecision decision =
        new BotDecision(
            Instant.now(),
            gameId,
            ctx.playerUUID(),
            player.name(),
            ctx.roundState().number(),
            ctx.roundState().tricks().size(),
            ctx.roundState(),
            ctx.legalCards(),
            card);

    final Deque<BotDecision> gameLog = log.computeIfAbsent(gameId, k -> new ArrayDeque<>());
    synchronized (gameLog) {
      gameLog.addLast(decision);
      while (gameLog.size() > MAX_PER_GAME) {
        gameLog.removeFirst();
      }
    }
  }

  /** Returns a snapshot of the rolling log for a game, oldest first. */
  public List<BotDecision> getDecisions(final UUID gameId) {
    final Deque<BotDecision> gameLog = log.get(gameId);
    if (gameLog == null) return List.of();
    synchronized (gameLog) {
      return List.copyOf(gameLog);
    }
  }

  /** Releases all stored decisions for a game. Called when the game is cleaned up. */
  public void clearGame(final UUID gameId) {
    log.remove(gameId);
    final String prefix = gameId + ":";
    pending.keySet().removeIf(k -> k.startsWith(prefix));
  }

  private static String key(final UUID gameId, final String playerName) {
    return gameId + ":" + playerName;
  }
}
