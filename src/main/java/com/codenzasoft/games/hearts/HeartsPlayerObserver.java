package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import java.util.List;
import java.util.Set;

public interface HeartsPlayerObserver {

  void yourTurnToPlay(HeartsGame game, HeartsState state, Player player, Set<Card> validCards);

  void yourTurnToPass(HeartsGame game, HeartsState state, Player player, List<Card> hand);
}
