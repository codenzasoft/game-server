package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Deck;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class HeartsGame {

  public static final Card QUEEN_OF_SPADES = new Card(Suit.SPADES, Rank.QUEEN);
  public static final Card TWO_OF_CLUBS = new Card(Suit.CLUBS, Rank.TWO);

  public static class Builder {
    private List<Player> players;
    private UUID id;

    public Builder withPlayers(final List<Player> players) {
      this.players = players;
      return this;
    }

    public Builder withId(final UUID id) {
      this.id = id;
      return this;
    }

    public HeartsGame build() {
      return new HeartsGame(GameState.with(id, players));
    }
  }

  private GameState gameState;

  private HeartsGame(final GameState gameState) {
    setGameState(gameState);
  }

  private void setGameState(final GameState gameState) {
    this.gameState = gameState;
  }

  public GameState getGameState() {
    return gameState;
  }

  public List<Player> getPlayers() {
    return getGameState().players();
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

  public static Player getPlayerRelativeTo(
      final Player player, final Pass pass, List<Player> players) {
    int index = Math.floorMod(players.indexOf(player) + pass.getOffset(), players.size());
    return players.get(index);
  }

  public static Player getPlayerLeftOf(final Player player, final List<Player> players) {
    return getPlayerRelativeTo(player, Pass.LEFT, players);
  }

  public synchronized void startNewRound() {
    final Player dealer =
        getGameState()
            .getCurrentRound()
            .map(r -> getPlayerLeftOf(r.dealer(), getPlayers()))
            .orElse(getPlayers().get(0));
    final Deck deck = new Deck.Builder().build();
    deck.shuffle();
    final Map<Player, List<Card>> deal = deck.dealAll(getPlayers());
    final Map<Player, List<Card>> setDeal =
        deal.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, e -> Collections.unmodifiableList(e.getValue())));
    startNewRound(dealer, setDeal);
  }

  public synchronized void startNewRound(final Player dealer, final Map<Player, List<Card>> deal) {
    setGameState(getGameState().startNewRound(dealer, deal));
  }

  public synchronized void setPass(final Player player, final List<Card> cards) {
    setGameState(getGameState().setPass(player, cards));
  }

  public synchronized void passCards() {
    setGameState(getGameState().passCards());
  }

  public synchronized void startNewTrick() {
    setGameState(getGameState().startNewTrick());
  }

  public synchronized void playCard(final Player player, final Card card) {
    setGameState(getGameState().playCard(player, card));
  }
}
