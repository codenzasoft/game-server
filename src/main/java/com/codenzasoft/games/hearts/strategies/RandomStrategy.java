package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsPlayerObserver;
import com.codenzasoft.games.hearts.HeartsState;
import java.util.*;

public class RandomStrategy implements HeartsPlayerObserver {

  private final Random random = new Random();

  @Override
  public void yourTurnToPlay(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final Set<Card> validCards) {
    final Card card = getRandomCard(new ArrayList<>(validCards));
    game.playCard(player, card);
  }

  @Override
  public void yourTurnToPass(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> hand) {
    final List<Card> shuffled = new ArrayList<>(hand);
    Collections.shuffle(shuffled, random);
    game.setPass(player, shuffled.subList(0, 3));
  }

  private Card getRandomCard(final List<Card> cards) {
    return cards.get(random.nextInt(cards.size()));
  }
}
