package com.codenzasoft.games.kingshand;

public record TimeState(Season season, Phase phase, int year) {

  public TimeState advance(ResourceState resources) {

    Phase nextPhase = phase();
    int nextYear = year;
    Season nextSeason = season;

    //    if (season == Season.WINTER &&  && resources.unlocked().contains(ResourceTarget.GOLD)) {
    //      nextPhase = Phase.COUNCIL_ADD;
    //    } else {

    switch (phase) {
      case PLAY:
        if (season == Season.WINTER && resources.unlocked().contains(ResourceTarget.GOLD)) {
          nextPhase = Phase.COUNCIL_ADD;
          break;
        } else if (season == Season.WINTER) {
          nextSeason = season.next();
          nextYear = year + 1;
          nextPhase = Phase.ADD;
          break;
        }

        nextPhase = Phase.ADD;
        nextSeason = season.next();
        break;

      case ADD:
        nextPhase = Phase.PLAY;
        break;
      case COUNCIL_ADD:
        nextPhase = Phase.COUNCIL_MEETING;
        break;
      case COUNCIL_MEETING:
        nextPhase = Phase.ADD;
        nextYear = year + 1;
        nextSeason = season.next();
        break;
    }
    // }

    return new TimeState(nextSeason, nextPhase, nextYear);
  }
}
