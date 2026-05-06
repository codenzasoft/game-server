package com.codenzasoft.games.common;

import java.util.List;
import java.util.UUID;

public interface Game<T> {

  GameType getGameType();

  T getGame();

  UUID getId();

  List<Player> getPlayers();
}
