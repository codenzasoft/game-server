package com.codenzasoft.games.spring.views;

import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.GameEntity;
import com.codenzasoft.games.spring.entities.RoleEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Helper methods for Thymeleaf views. */
@Component
public class ViewHelper {

  @Value("${service.uri.xo.ui}")
  private String xoUri;

  /**
   * Returns whether the provided user has the {@link Role#ADMIN} role.
   *
   * @param user A {@link UserEntity}.
   * @return Whether the user has admin privileges.
   */
  public boolean isAdmin(final UserEntity user) {
    return user.getRoles().stream().map(RoleEntity::getRole).anyMatch(Role::isAdmin);
  }

  public URI getGameURI(final GameEntity game) {
    return switch (game.getGameType()) {
      case XO -> getXoURI(game);
      default -> URI.create("");
    };
  }

  public URI getXoURI(final GameEntity game) {
    URI base = URI.create(xoUri);
    return base.resolve(game.getId().toString());
  }
}
