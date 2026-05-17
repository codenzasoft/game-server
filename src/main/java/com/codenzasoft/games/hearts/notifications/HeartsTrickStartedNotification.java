package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;

public class HeartsTrickStartedNotification extends HeartsNotification {

  private final RoundState round;
  private final TrickState trick;

  public HeartsTrickStartedNotification(final RoundState round, final TrickState trick) {
    this.round = round;
    this.trick = trick;
  }

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.trickStarted(game, state, round, trick);
  }
}
