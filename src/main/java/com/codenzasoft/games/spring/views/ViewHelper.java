package com.codenzasoft.games.spring.views;

import com.codenzasoft.games.spring.config.Role;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/** Helper methods for Thymeleaf views. */
@Component
public class ViewHelper {

  /**
   * Returns whether the provided user has the {@link Role#ADMIN}.
   *
   * @param user The logged-in user
   * @return Whether the user has admin privileges.
   */
  public boolean isAdmin(final UserDetails user) {
    return user.getAuthorities().stream()
        .map(Role::fromGrantedAuthority)
        .anyMatch(Role.ADMIN::equals);
  }
}
