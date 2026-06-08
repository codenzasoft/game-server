package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import java.util.Set;

public abstract class Rule {

  public abstract Set<Card> getValidCards(final Player player, final RoundState round);
}
