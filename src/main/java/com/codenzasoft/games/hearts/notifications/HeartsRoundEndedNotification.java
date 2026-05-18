package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;

public class HeartsRoundEndedNotification extends HeartsNotification {

  private final RoundState round;

  public HeartsRoundEndedNotification(final RoundState round) {
    this.round = round;
  }

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.roundEnded(game, state);
  }
}
