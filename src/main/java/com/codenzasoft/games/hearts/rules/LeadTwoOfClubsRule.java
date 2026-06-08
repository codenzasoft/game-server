package com.codenzasoft.games.hearts.rules;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.Rule;
import com.codenzasoft.games.hearts.TrickState;
import java.util.Set;
import java.util.stream.Collectors;

/** A round of hearts must be lead with the 2 of clubs. */
public class LeadTwoOfClubsRule extends Rule {

  @Override
  public Set<Card> getValidCards(final Player player, final RoundState round) {
    final TrickState trick = round.getTrick();
    if (trick.number() == 1 && trick.cards().isEmpty()) {
      return round.getHand(player).stream()
          .filter(c -> c.equals(HeartsGame.TWO_OF_CLUBS))
          .collect(Collectors.toSet());
    }
    return Set.of();
  }
}
