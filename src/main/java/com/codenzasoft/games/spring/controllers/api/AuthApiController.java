package com.codenzasoft.games.spring.controllers.api;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

  record LoginRequest(String username, String password) {}

  @Autowired private AuthenticationManager authenticationManager;
  @Autowired private GamesUserDetailsService gamesUserDetailsService;

  /**
   * Authenticates with username/password and establishes a session cookie for subsequent requests.
   */
  @PostMapping("/login")
  public ResponseEntity<?> login(
      @RequestBody final LoginRequest request,
      final HttpServletRequest httpRequest,
      final HttpServletResponse httpResponse) {
    try {
      final Authentication auth =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  request.username(), request.password()));
      final SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
      httpRequest
          .getSession(true)
          .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
      return gamesUserDetailsService
          .getUserByUsernameOrEmail(auth.getName())
          .map(user -> ResponseEntity.ok((Object) user))
          .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    } catch (AuthenticationException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("error", "Invalid credentials"));
    }
  }

  /** Invalidates the current session, clearing the server-side session record. */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(final HttpServletRequest request) {
    final HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }

  /** Returns the authenticated user's profile; useful for app startup re-auth checks. */
  @GetMapping("/me")
  public ResponseEntity<?> me(final Principal principal) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
    }
    final Optional<UserEntity> user =
        gamesUserDetailsService.getUserByUsernameOrEmail(principal.getName());
    return user.map(u -> ResponseEntity.ok((Object) u))
        .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }
}
