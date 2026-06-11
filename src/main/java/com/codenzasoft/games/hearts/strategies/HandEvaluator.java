package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.HeartsGame;
import java.util.List;

/**
 * Evaluates a dealt hand's moon-shooting potential. Used at pass time to decide whether a bot
 * should attempt to shoot the moon this round.
 *
 * <p>Scoring weights:
 *
 * <ul>
 *   <li>A♥, K♥ — +3 each: essential control; losing either hands the trick to an opponent
 *   <li>Q♥, J♥ — +2 each: strong heart control
 *   <li>10♥, 9♥ — +1 each: useful but not decisive
 *   <li>Q♠ — +2: must be captured when shooting; holding it avoids the 13-pt surprise
 *   <li>A♠, K♠ — +1 each: spade control, prevents forced losses via spade leads
 *   <li>A♣, A♦ — +1 each: trick control in side suits
 * </ul>
 */
public final class HandEvaluator {

  /** Minimum score at which a bot will attempt to shoot the moon. Tunable. */
  public static final int SHOOT_THRESHOLD = 10;

  private HandEvaluator() {}

  public static int shootingScore(final List<Card> hand) {
    return hand.stream().mapToInt(HandEvaluator::cardScore).sum();
  }

  public static boolean isShootViable(final List<Card> hand) {
    return shootingScore(hand) >= SHOOT_THRESHOLD;
  }

  private static int cardScore(final Card card) {
    if (card.getSuit() == Suit.HEARTS) {
      return switch (card.getRank()) {
        case ACE, KING -> 3;
        case QUEEN, JACK -> 2;
        case TEN, NINE -> 1;
        default -> 0;
      };
    }
    if (card.equals(HeartsGame.QUEEN_OF_SPADES)) {
      return 2;
    }
    if (card.getSuit() == Suit.SPADES && card.outRanks(Rank.QUEEN)) {
      return 1; // A♠ or K♠
    }
    if (card.getRank() == Rank.ACE) {
      return 1; // A♣ or A♦
    }
    return 0;
  }
}
