package com.codenzasoft.games.spring.views;

import com.codenzasoft.games.spring.config.Role;
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
}
