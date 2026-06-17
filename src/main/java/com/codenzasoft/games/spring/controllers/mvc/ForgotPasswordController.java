package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.services.PasswordResetService;
import com.codenzasoft.games.spring.validation.PasswordValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
class ForgotPasswordController {

  @Autowired private GamesUserDetailsService userDetailsService;
  @Autowired private PasswordResetService passwordResetService;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private UsersRepository usersRepository;

  @GetMapping("/forgot-password")
  String forgotPasswordForm(Model model) {
    model.addAttribute(PageConstants.PAGE_TITLE, "Forgot Password | Codenza");
    return "forgot-password";
  }

  @PostMapping("/forgot-password")
  String forgotPasswordSubmit(
      @RequestParam String identifier,
      HttpServletRequest request,
      RedirectAttributes redirectAttrs) {

    final Optional<UserEntity> user =
        userDetailsService.getUserByUsernameOrEmail(identifier.trim());

    if (user.isPresent() && user.get().getEmail() != null) {
      final String resetBaseUrl =
          ServletUriComponentsBuilder.fromContextPath(request)
              .path("/reset-password")
              .toUriString();
      passwordResetService.initiateReset(user.get(), resetBaseUrl);
    }

    // Always show the same message to prevent account enumeration
    redirectAttrs.addFlashAttribute(
        "success",
        "If an account with that username or email exists, a password reset link has been sent.");
    return "redirect:/forgot-password";
  }

  @GetMapping("/reset-password")
  String resetPasswordForm(@RequestParam String token, Model model) {
    model.addAttribute(PageConstants.PAGE_TITLE, "Reset Password | Codenza");
    final Optional<UserEntity> user = passwordResetService.validateToken(token);
    if (user.isEmpty()) {
      model.addAttribute("error", "This password reset link is invalid or has expired.");
      return "reset-password";
    }
    model.addAttribute("token", token);
    return "reset-password";
  }

  @PostMapping("/reset-password")
  String resetPasswordSubmit(
      @RequestParam String token,
      @RequestParam String newPassword,
      @RequestParam String confirmPassword,
      RedirectAttributes redirectAttrs) {

    final Optional<UserEntity> optUser = passwordResetService.validateToken(token);
    if (optUser.isEmpty()) {
      redirectAttrs.addFlashAttribute(
          "error", "This password reset link is invalid or has expired.");
      return "redirect:/forgot-password";
    }

    if (!newPassword.equals(confirmPassword)) {
      redirectAttrs.addFlashAttribute("error", "Passwords do not match.");
      return "redirect:/reset-password?token=" + token;
    }

    final PasswordValidator.Result result = PasswordValidator.validate(newPassword);
    if (!result.isValid()) {
      redirectAttrs.addFlashAttribute("error", result.getErrorMessage());
      return "redirect:/reset-password?token=" + token;
    }

    final UserEntity user = optUser.get();
    user.setPassword(passwordEncoder.encode(newPassword));
    usersRepository.save(user);
    passwordResetService.consumeToken(token);

    redirectAttrs.addFlashAttribute("success", "Your password has been reset. Please sign in.");
    return "redirect:/login";
  }
}
