package com.codenzasoft.games.xando;

public record WinLoss(int wins, int losses) implements Comparable<WinLoss> {

  public WinLoss add(WinLoss winLoss) {
    return new WinLoss(wins + winLoss.wins(), losses + winLoss.losses());
  }

  @Override
  public int compareTo(final WinLoss wl) {
    if (losses == 0) {
      if (wl.losses() > 0) {
        return 1;
      }
      return wins - wl.wins();
    } else {
      // losses > 0
      return wl.losses() - losses;
    }
  }
}
