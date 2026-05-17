package com.codenzasoft.games.hearts.rules;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.Rule;
import com.codenzasoft.games.hearts.TrickState;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** A player must follow the lead suit in a trick if they have the suit. */
public class FollowSuitRule extends Rule {

  @Override
  public Set<Card> getValidCards(final Player player, final RoundState round) {
    final TrickState trick = round.getTrick();
    Optional<Suit> suit = trick.getTrump();
    if (suit.isPresent()) {
      final Set<Card> matching =
          round.getHand(player).stream()
              .filter(c -> c.getSuit().equals(suit.get()))
              .collect(Collectors.toSet());
      if (matching.isEmpty()) {
        if (trick.number() == 1) {
          // cannot play a point in first trick
          final Set<Card> allowed =
              round.getHand(player).stream()
                  .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
                  .filter(c -> !c.getSuit().equals(Suit.HEARTS))
                  .collect(Collectors.toSet());
          if (!allowed.isEmpty()) {
            return allowed;
          }
        }
        // any remaining card can be played when no trump present or no non-point cards
        return new HashSet<>(round.getHand(player));
      } else {
        return matching;
      }
    }
    return Set.of();
  }
}
