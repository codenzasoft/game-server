package com.codenzasoft.games.kingshand;

public record TimeState(Season season, Phase phase, int year) {

  public TimeState advance(ResourceState resources) {
    if (season == Season.WINTER
        && phase == Phase.PLAY
        && resources.unlocked().contains(ResourceTarget.GOLD)) {
      // enter the COUNCIL_ADD phase at the end of winter play if gold is unlocked
      return withPhase(Phase.COUNCIL_ADD);
    } else {
      return switch (phase) {
        case PLAY, COUNCIL_MEETING ->
            // enter the ADD phase of the next season, advancing the year after winter
            withPhase(Phase.ADD).advanceSeason();
        case ADD -> withPhase(Phase.PLAY);
        case COUNCIL_ADD -> withPhase(Phase.COUNCIL_MEETING);
      };
    }
  }

  private TimeState withPhase(Phase phase) {
    return new TimeState(season, phase, year);
  }

  /**
   * Returns a new {@link TimeState} in the next {@link Season}. The year is advanced at the end of
   * WINTER.
   *
   * @return A {@link TimeState} in the next {@link Season}.
   */
  private TimeState advanceSeason() {
    return new TimeState(season.next(), phase(), season == Season.WINTER ? year + 1 : year);
  }
}
