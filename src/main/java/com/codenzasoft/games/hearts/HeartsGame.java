package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Deck;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.notifications.*;
import com.codenzasoft.games.hearts.rules.*;
import java.util.*;
import java.util.Set;
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
    private final Map<String, HeartsPlayerObserver> playerObservers = new HashMap<>();
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

    public Builder addPlayerObserver(final Player player, final HeartsPlayerObserver observer) {
      playerObservers.put(player.name(), observer);
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
          executor,
          playerObservers);
    }
  }

  private static final List<Rule> RULES =
      List.of(new LeadTwoOfClubsRule(), new FollowSuitRule(), new LeadTrickRule());

  private static final HeartsGameStartedNotification GAME_STARTED_NOTIFICATION =
      new HeartsGameStartedNotification();
  private static final HeartsGameOverNotification GAME_OVER_NOTIFICATION =
      new HeartsGameOverNotification();
  private static final HeartsGameQuitNotification GAME_QUIT_NOTIFICATION =
      new HeartsGameQuitNotification();
  private static final HeartsPassCompleteNotification PASS_COMPLETE_NOTIFICATION =
      new HeartsPassCompleteNotification();
  private static final HeartsRoundStartedNotification ROUND_STARTED_NOTIFICATION =
      new HeartsRoundStartedNotification();
  private static final HeartsRoundEndedNotification ROUND_ENDED_NOTIFICATION =
      new HeartsRoundEndedNotification();
  private static final HeartsTrickStartedNotification TRICK_STARTED_NOTIFICATION =
      new HeartsTrickStartedNotification();
  private static final HeartsTrickEndedNotification TRICK_ENDED_NOTIFICATION =
      new HeartsTrickEndedNotification();

  private final UUID id;
  private HeartsState gameState;
  private final List<Player> players;
  private final List<HeartsListener> listeners;
  private final Executor executor;
  private final Map<String, HeartsPlayerObserver> playerObservers;

  private HeartsGame(
      final UUID id,
      final HeartsState gameState,
      final List<Player> players,
      final List<HeartsListener> listeners,
      final Executor executor,
      final Map<String, HeartsPlayerObserver> playerObservers) {
    this.id = id;
    this.players = players;
    this.listeners = listeners;
    this.executor = executor;
    this.playerObservers = Map.copyOf(playerObservers);
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

  public Set<Card> getValidCards(final Player player) {
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    for (final Rule rule : RULES) {
      final Set<Card> valid = rule.getValidCards(player, round);
      if (!valid.isEmpty()) {
        return valid;
      }
    }
    throw new IllegalStateException("no valid cards found for " + player.name());
  }

  private Player findPlayer(final String name) {
    return players.stream()
        .filter(p -> p.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("player not found: " + name));
  }

  private void notifyPlayerToPlay(final String playerName) {
    final HeartsPlayerObserver observer = playerObservers.get(playerName);
    if (observer == null) return;
    final Player player = findPlayer(playerName);
    final HeartsState state = getGameState();
    final Set<Card> valid = getValidCards(player);
    executor.execute(() -> observer.yourTurnToPlay(this, state, player, valid));
  }

  private void notifyPlayerToPass(final String playerName) {
    final HeartsPlayerObserver observer = playerObservers.get(playerName);
    if (observer == null) return;
    final Player player = findPlayer(playerName);
    final HeartsState state = getGameState();
    final List<Card> hand =
        state.getCurrentRound().map(r -> r.hands().get(playerName)).orElse(List.of());
    executor.execute(() -> observer.yourTurnToPass(this, state, player, hand));
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
    notifyListeners(ROUND_STARTED_NOTIFICATION);
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    if (round.pass() != Pass.KEEPER) {
      players.forEach(p -> notifyPlayerToPass(p.name()));
    }
  }

  private synchronized void startNewRound(final String dealer, final Map<String, List<Card>> deal) {
    setGameState(getGameState().startNewRound(dealer, deal));
  }

  public synchronized void setPass(final Player player, final List<Card> cards) {
    setGameState(getGameState().setPass(player.name(), cards));
    notifyListeners(new HeartsPassSelectedNotification(player, cards));
  }

  public synchronized void passCards() {
    setGameState(getGameState().passCards());
    notifyListeners(PASS_COMPLETE_NOTIFICATION);
  }

  public synchronized void startNewTrick() {
    setGameState(getGameState().startNewTrick());
    notifyListeners(TRICK_STARTED_NOTIFICATION);
    getGameState()
        .getCurrentRound()
        .flatMap(r -> r.getNextPlayer(getGameState()))
        .ifPresent(this::notifyPlayerToPlay);
  }

  public synchronized void playCard(final Player player, final Card card) {
    final Set<Card> validCards = getValidCards(player);
    if (!validCards.contains(card)) {
      throw new IllegalStateException("card " + card + " is not valid for " + player.name());
    }
    setGameState(getGameState().playCard(player.name(), card));
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    final TrickState trick = getGameState().getCurrentTrick().orElseThrow();
    notifyListeners(new HeartsCardPlayedNotification(player, card, trick.cards().size()));
    if (trick.isComplete()) {
      notifyListeners(TRICK_ENDED_NOTIFICATION);
      if (round.isComplete()) {
        notifyListeners(ROUND_ENDED_NOTIFICATION);
      }
    } else {
      round.getNextPlayer(getGameState()).ifPresent(this::notifyPlayerToPlay);
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
