package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.services.EmailVerificationService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/verify-email")
public class VerifyEmailController {

  @Autowired private EmailVerificationService verificationService;
  @Autowired private GamesUserDetailsService userDetailsService;
  @Autowired private UsersRepository usersRepository;

  @GetMapping
  public String verify(@RequestParam(required = false) String token, Model model) {
    if (token == null || token.isBlank()) {
      model.addAttribute("error", "Missing verification token.");
      return "verify-email";
    }

    final Optional<UserEntity> user = verificationService.validateToken(token);
    if (user.isEmpty()) {
      model.addAttribute(
          "error", "This verification link is invalid or has expired. Please request a new one.");
      return "verify-email";
    }

    verificationService.verifyUser(user.get(), token);
    model.addAttribute("verified", true);
    return "verify-email";
  }

  @GetMapping("/resend")
  public String showResend(Model model) {
    model.addAttribute(PageConstants.PAGE_TITLE, "Resend verification | Codenza");
    return "verify-email-resend";
  }

  @PostMapping("/resend")
  public String resend(
      @RequestParam String identifier,
      HttpServletRequest request,
      RedirectAttributes redirectAttrs) {
    final Optional<UserEntity> user =
        userDetailsService.getUserByUsernameOrEmail(identifier == null ? "" : identifier.trim());

    if (user.isPresent() && !user.get().isEmailVerified() && user.get().getEmail() != null) {
      final String baseUrl =
          org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromContextPath(
                  request)
              .path("/verify-email")
              .toUriString();
      verificationService.sendVerification(user.get(), baseUrl);
    }

    // Always show success to avoid account enumeration.
    redirectAttrs.addFlashAttribute(
        "success",
        "If that account exists and is unverified, a new verification email has been sent.");
    return "redirect:/verify-email/resend";
  }
}
