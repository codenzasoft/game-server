package com.codenzasoft.games.spring.views;

import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.GameEntity;
import com.codenzasoft.games.spring.entities.RoleEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Helper methods for Thymeleaf views. */
@Component
public class ViewHelper {

  /**
   * Returns whether the provided user has the {@link Role#ADMIN} role.
   *
   * @param user A {@link UserEntity}.
   * @return Whether the user has admin privileges.
   */
  public boolean isAdmin(final UserEntity user) {
    return user.getRoles().stream().map(RoleEntity::getRole).anyMatch(Role::isAdmin);
  }

  /**
   * Returns whether the provided game is in the lobby phase (waiting for players to join).
   *
   * @param game A {@link GameEntity}.
   * @return {@code true} if the game has not yet reached its required player count.
   */
  public boolean isLobby(final GameEntity game) {
    return switch (game.getGameType()) {
      case HEARTS -> game.getPlayers().size() < 4;
      case XO -> game.getPlayers().size() < 2;
      case KH -> false;
    };
  }

  public String getGameUrl(final GameEntity game) {
    return switch (game.getGameType()) {
      case XO -> "/games/xo/" + game.getId().toString();
      case KH -> "/games/kh/" + game.getId().toString();
      case HEARTS -> "/games/hearts/" + game.getId().toString();
    };
  }

  /**
   * Returns a display string describing the players of a game. When all opponents are bots the
   * string reads "player vs. Bots"; otherwise each opponent's username is listed individually.
   *
   * @param players The ordered list of players in the game.
   * @return A human-readable player summary.
   */
  public String getGamePlayersDescription(final List<UserEntity> players) {
    if (players.isEmpty()) return "";
    if (players.size() == 1) return players.get(0).getUsername();
    final String first = players.get(0).getUsername();
    final List<UserEntity> opponents = players.subList(1, players.size());
    if (opponents.stream().allMatch(UserEntity::isBot)) {
      return first + " vs. Bots";
    }
    return first
        + " vs. "
        + opponents.stream().map(UserEntity::getUsername).collect(Collectors.joining(", "));
  }

  /**
   * Returns a user-friendly display name for the type of the provided game.
   *
   * @param game A {@link GameEntity}.
   * @return A human-readable game type label.
   */
  public String getGameTypeName(final GameEntity game) {
    return switch (game.getGameType()) {
      case XO -> "Xs & Os";
      case KH -> "King's Hand";
      case HEARTS -> "Hearts";
    };
  }
}
