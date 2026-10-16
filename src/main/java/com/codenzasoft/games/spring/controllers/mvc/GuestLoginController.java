package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.RoleEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class GuestLoginController {

  private static final String NAME_PATTERN = "^[a-zA-Z0-9_-]+$";
  private static final int MIN_LENGTH = 3;
  private static final int MAX_LENGTH = 25;

  @Autowired private UsersRepository usersRepository;

  @PostMapping("/guest/login")
  @Transactional
  public String guestLogin(
      @RequestParam String displayName,
      HttpServletRequest request,
      RedirectAttributes redirectAttrs) {

    final String trimmed = displayName == null ? "" : displayName.trim();

    if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
      redirectAttrs.addFlashAttribute(
          "guestError", "Name must be " + MIN_LENGTH + "–" + MAX_LENGTH + " characters.");
      return "redirect:/login";
    }
    if (!trimmed.matches(NAME_PATTERN)) {
      redirectAttrs.addFlashAttribute(
          "guestError", "Name may only contain letters, digits, underscores, and hyphens.");
      return "redirect:/login";
    }

    String storedName = null;
    for (int i = 0; i < 5; i++) {
      String candidate =
          trimmed + "#" + String.format("%04x", ThreadLocalRandom.current().nextInt(0x10000));
      if (usersRepository.findByUserName(candidate) == null) {
        storedName = candidate;
        break;
      }
    }
    if (storedName == null) {
      redirectAttrs.addFlashAttribute(
          "guestError", "Could not create guest session. Please try again.");
      return "redirect:/login";
    }

    final UserEntity user = new UserEntity();
    user.setUserName(storedName);
    user.setEmailVerifiedAt(Instant.now());

    final UserEntity saved = usersRepository.save(user);
    saved.getRoles().add(new RoleEntity(saved.getId(), Role.GUEST));
    usersRepository.save(saved);

    final List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("GUEST"));
    final Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(storedName, null, authorities);
    final SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);

    final HttpSession session = request.getSession(true);
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

    return "redirect:/";
  }
}
