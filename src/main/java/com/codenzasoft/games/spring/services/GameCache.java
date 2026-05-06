package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.GameType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GameCache {

  private final Map<UUID, Game<?>> games = new HashMap<>();

  public <T> Optional<Game<T>> getGame(final UUID uuid, final Class<T> clazz) {
    final Optional<Game<?>> someGame = Optional.ofNullable(games.get(uuid));
    if (someGame.isPresent()) {
      final Game<?> game = someGame.get();
      if (clazz.isInstance(game.getGame())) {
        // TODO: is there a way to avoid the cast?
        return Optional.of((Game<T>) game);
      }
    }
    return Optional.empty();
  }

  public <T> Optional<Game<T>> getGameOfType(final UUID uuid, final GameType type) {
    final Optional<Game<?>> someGame = Optional.ofNullable(games.get(uuid));
    if (someGame.isPresent()) {
      final Game<?> game = someGame.get();
      if (game.getGameType().equals(type)) {
        // TODO: is there a way to avoid the cast?
        return Optional.of((Game<T>) game);
      }
    }
    return Optional.empty();
  }

  public boolean addGame(final Game<?> game) {
    if (games.containsKey(game.getId())) {
      return false;
    }
    games.put(game.getId(), game);
    return true;
  }

  public boolean removeGame(final UUID uuid) {
    return Optional.ofNullable(games.remove(uuid)).isPresent();
  }

  public boolean removeGame(final Game<?> game) {
    return removeGame(game.getId());
  }
}
