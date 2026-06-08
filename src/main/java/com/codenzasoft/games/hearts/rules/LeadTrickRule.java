package com.codenzasoft.games.hearts.rules;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.Rule;
import com.codenzasoft.games.hearts.TrickState;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** A player cannot lead hearts or queen of spades until a heart has been played. */
public class LeadTrickRule extends Rule {

  @Override
  public Set<Card> getValidCards(final Player player, final RoundState round) {
    final TrickState trick = round.getTrick();
    if (trick.number() > 1 && trick.cards().isEmpty()) {
      if (round.getPlayedCards().stream().anyMatch(card -> card.getSuit().equals(Suit.HEARTS))) {
        // if a heart has been taken, any card can be played
        return new HashSet<>(round.getHand(player));
      } else {
        // otherwise, only a non heart card can be played
        Set<Card> noHearts =
            round.getHand(player).stream()
                .filter(card -> !card.getSuit().equals(Suit.HEARTS))
                .collect(Collectors.toSet());
        if (noHearts.isEmpty()) {
          // however, if only hearts remain, any can be played
          return new HashSet<>(round.getHand(player));
        } else {
          return noHearts;
        }
      }
    }
    return Set.of();
  }
}
