package com.codenzasoft.games.kingshand;

public record TimeState(Season season, Phase phase, int year) {

  public TimeState advance(ResourceState resources) {

    Phase nextPhase = phase();
    int nextYear = year;
    Season nextSeason = season;

    if (season == Season.WINTER
        && phase == Phase.PLAY
        && resources.unlocked().contains(ResourceTarget.GOLD)) {
      // enter the COUNCIL_ADD phase at the end of winter play if gold is unlocked
      nextPhase = Phase.COUNCIL_ADD;
    } else {
      switch (phase) {
        case PLAY:
        case COUNCIL_MEETING:
          // enter the ADD phase of the next season, advancing the year at year-end
          nextPhase = Phase.ADD;
          nextSeason = season.next();
          if (season == Season.WINTER) {
            nextYear = year + 1;
          }
          break;
        case ADD:
          nextPhase = Phase.PLAY;
          break;
        case COUNCIL_ADD:
          nextPhase = Phase.COUNCIL_MEETING;
          break;
      }
    }

    return new TimeState(nextSeason, nextPhase, nextYear);
  }
}
