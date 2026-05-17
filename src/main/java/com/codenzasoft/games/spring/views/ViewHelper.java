package com.codenzasoft.games.spring.views;

import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.GameEntity;
import com.codenzasoft.games.spring.entities.RoleEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
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

  public String getGameUrl(final GameEntity game) {
    return switch (game.getGameType()) {
      case XO -> "/games/xo/" + game.getId().toString();
      case KH -> "/games/kh/" + game.getId().toString();
      case HEARTS -> "/games/hearts/" + game.getId().toString();
    };
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
