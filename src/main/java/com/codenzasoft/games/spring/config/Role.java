package com.codenzasoft.games.spring.config;

import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;

/** Roles used for authorities / permissions. */
public enum Role {
  ADMIN,
  DEVELOPER,
  USER,
  BOT;

  public static Role fromString(String role) {
    return switch (role.toUpperCase()) {
      case "ADMIN" -> Role.ADMIN;
      case "DEVELOPER" -> Role.DEVELOPER;
      case "USER" -> Role.USER;
      case "BOT" -> Role.BOT;
      default -> null;
    };
  }

  public static Role fromGrantedAuthority(GrantedAuthority grantedAuthority) {
    return fromString(Objects.requireNonNull(grantedAuthority.getAuthority()));
  }

  public boolean isAdmin() {
    return this == Role.ADMIN;
  }
}
