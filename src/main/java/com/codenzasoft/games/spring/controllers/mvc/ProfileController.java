package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.views.ViewHelper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The {@link ProfileController} serves the profile page where a user can view and edit their
 * account details. Its corresponding template is in <code>resources/templates/profile.html</code>.
 */
@Controller
public class ProfileController {

  @Autowired GamesUserDetailsService gamesUserDetailsService;
  @Autowired UsersRepository usersRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired ViewHelper viewHelper;

  @GetMapping("/profile")
  public String profile(Model model, @AuthenticationPrincipal UserDetails principal) {
    final Optional<UserEntity> optionalUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(principal.getUsername());
    if (optionalUser.isPresent()) {
      model.addAttribute(PageConstants.PAGE_TITLE, "Profile");
      model.addAttribute(PageConstants.PAGE_USER, optionalUser.get());
      model.addAttribute(PageConstants.PAGE_HELPER, viewHelper);
    }
    return "profile";
  }

  @PostMapping("/profile")
  public String updateProfile(
      @RequestParam String userName,
      @RequestParam String email,
      @RequestParam(required = false) String currentPassword,
      @RequestParam(required = false) String newPassword,
      @RequestParam(required = false) String confirmPassword,
      @AuthenticationPrincipal UserDetails principal,
      HttpServletRequest request,
      RedirectAttributes redirectAttrs) {

    final Optional<UserEntity> optionalUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(principal.getUsername());
    if (optionalUser.isEmpty()) {
      return "redirect:/login";
    }

    final UserEntity user = optionalUser.get();

    if (newPassword != null && !newPassword.isBlank()) {
      if (currentPassword == null
          || !passwordEncoder.matches(currentPassword, user.getPassword())) {
        redirectAttrs.addFlashAttribute("error", "Current password is incorrect.");
        return "redirect:/profile";
      }
      if (!newPassword.equals(confirmPassword)) {
        redirectAttrs.addFlashAttribute("error", "New passwords do not match.");
        return "redirect:/profile";
      }
      user.setPassword(passwordEncoder.encode(newPassword));
    }

    final boolean usernameChanged = !user.getUsername().equals(userName);
    user.setUserName(userName);
    user.setEmail(email);
    usersRepository.save(user);

    if (usernameChanged) {
      request.getSession().invalidate();
      return "redirect:/login";
    }

    redirectAttrs.addFlashAttribute("success", "Profile updated successfully.");
    return "redirect:/profile";
  }
}
