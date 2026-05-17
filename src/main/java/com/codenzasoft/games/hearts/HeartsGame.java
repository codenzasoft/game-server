package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Deck;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.notifications.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class HeartsGame implements Game<HeartsGame> {

  public static final Card QUEEN_OF_SPADES = new Card(Suit.SPADES, Rank.QUEEN);
  public static final Card TWO_OF_CLUBS = new Card(Suit.CLUBS, Rank.TWO);

  public static class Builder {
    private List<Player> players;
    private UUID id;
    private final List<HeartsListener> listeners = new ArrayList<>();
    private Executor executor;

    public Builder withPlayers(final List<Player> players) {
      this.players = players;
      return this;
    }

    public Builder withId(final UUID id) {
      this.id = id;
      return this;
    }

    public Builder addListener(final HeartsListener listener) {
      listeners.add(listener);
      return this;
    }

    public Builder withNotificationExecutor(final Executor executor) {
      this.executor = executor;
      return this;
    }

    public HeartsGame build() {
      if (executor == null) {
        withNotificationExecutor(Executors.newSingleThreadExecutor());
      }
      return new HeartsGame(
          id,
          HeartsState.with(players.stream().map(Player::name).toList()),
          players,
          listeners,
          executor);
    }
  }

  private static final HeartsGameStartedNotification GAME_STARTED_NOTIFICATION =
      new HeartsGameStartedNotification();
  private static final HeartsGameOverNotification GAME_OVER_NOTIFICATION =
      new HeartsGameOverNotification();
  private static final HeartsGameQuitNotification GAME_QUIT_NOTIFICATION =
      new HeartsGameQuitNotification();

  private final UUID id;
  private HeartsState gameState;
  private final List<Player> players;
  private final List<HeartsListener> listeners;
  private final Executor executor;

  private HeartsGame(
      final UUID id,
      final HeartsState gameState,
      final List<Player> players,
      final List<HeartsListener> listeners,
      final Executor executor) {
    this.id = id;
    this.players = players;
    this.listeners = listeners;
    this.executor = executor;
    setGameState(gameState);
    notifyListeners(GAME_STARTED_NOTIFICATION);
  }

  private void setGameState(final HeartsState gameState) {
    this.gameState = gameState;
  }

  public HeartsState getGameState() {
    return gameState;
  }

  @Override
  public GameType getGameType() {
    return GameType.HEARTS;
  }

  @Override
  public HeartsGame getGame() {
    return this;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public List<Player> getPlayers() {
    return players;
  }

  void notifyListeners(final HeartsNotification notification) {
    final HeartsState state = getGameState();
    final HeartsGame game = this;
    for (final HeartsListener listener : listeners) {
      executor.execute(() -> notification.execute(game, state, listener));
    }
  }

  /**
   * Returns the point value of the provided {@link Card}.
   *
   * @param card A {@link Card}
   * @return The point value of the provided {@link Card}.
   */
  public static int getPoints(final Card card) {
    if (card.getSuit() == Suit.HEARTS) {
      return 1;
    }
    if (card.getSuit() == Suit.SPADES && card.getRank() == Rank.QUEEN) {
      return 13;
    }
    return 0;
  }

  public synchronized void startNewRound() {
    final String dealer =
        getGameState()
            .getCurrentRound()
            .map(r -> getGameState().getPlayerLeftOf(r.dealer()))
            .orElse(getPlayers().get(0).name());
    final Deck deck = new Deck.Builder().build();
    deck.shuffle();
    final Map<Player, List<Card>> deal = deck.dealAll(getPlayers());
    final Map<String, List<Card>> setDeal =
        deal.entrySet().stream()
            .collect(
                Collectors.toMap(
                    e -> e.getKey().name(), e -> Collections.unmodifiableList(e.getValue())));
    startNewRound(dealer, setDeal);
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    notifyListeners(new HeartsRoundStartedNotification(round));
  }

  private synchronized void startNewRound(final String dealer, final Map<String, List<Card>> deal) {
    setGameState(getGameState().startNewRound(dealer, deal));
  }

  public synchronized void setPass(final Player player, final List<Card> cards) {
    setGameState(getGameState().setPass(player.name(), cards));
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    notifyListeners(new HeartsPassSelectedNotification(round, player, cards));
  }

  public synchronized void passCards() {
    setGameState(getGameState().passCards());
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    notifyListeners(new HeartsPassCompleteNotification(round));
  }

  public synchronized void startNewTrick() {
    setGameState(getGameState().startNewTrick());
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    final TrickState trick = getGameState().getCurrentTrick().orElseThrow();
    notifyListeners(new HeartsTrickStartedNotification(round, trick));
  }

  public synchronized void playCard(final Player player, final Card card) {
    setGameState(getGameState().playCard(player.name(), card));
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    final TrickState trick = getGameState().getCurrentTrick().orElseThrow();
    notifyListeners(
        new HeartsCardPlayedNotification(round, trick, player, card, trick.cards().size()));
    if (trick.isComplete()) {
      notifyListeners(new HeartsTrickEndedNotification(round, trick));
      if (round.isComplete()) {
        notifyListeners(new HeartsRoundEndedNotification(round));
      }
    }
  }

  public synchronized void gameOver() {
    setGameState(getGameState().setOver());
    notifyListeners(GAME_OVER_NOTIFICATION);
  }

  public synchronized void quit() {
    notifyListeners(GAME_QUIT_NOTIFICATION);
  }
}
