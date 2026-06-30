package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.RoleEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.services.EmailVerificationService;
import com.codenzasoft.games.spring.validation.PasswordValidator;
import com.codenzasoft.games.spring.validation.UsernameValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Handles self-service account creation. Whitelist code is retained but bypassed — accounts are now
 * open to anyone with a valid email address, gated by email verification instead.
 */
@Controller
public class RegisterController {

  private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

  @Autowired UsersRepository usersRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired EmailVerificationService verificationService;

  @GetMapping("/register")
  public String showForm(Model model) {
    model.addAttribute(PageConstants.PAGE_TITLE, "Create your account | Codenza");
    return "register";
  }

  @PostMapping("/register")
  @Transactional
  public String register(
      @RequestParam String email,
      @RequestParam String userName,
      @RequestParam String password,
      @RequestParam String confirmPassword,
      HttpServletRequest request,
      RedirectAttributes redirectAttrs) {

    final String trimmedEmail = email == null ? "" : email.trim().toLowerCase();
    final String trimmedUserName = userName == null ? "" : userName.trim();

    // ----- Input validation -----
    if (trimmedEmail.isEmpty()
        || trimmedUserName.isEmpty()
        || password == null
        || password.isEmpty()
        || confirmPassword == null
        || confirmPassword.isEmpty()) {
      return failed("All fields are required.", trimmedEmail, trimmedUserName, redirectAttrs);
    }
    if (!trimmedEmail.matches(EMAIL_PATTERN)) {
      return failed(
          "\"" + trimmedEmail + "\" is not a valid email.",
          trimmedEmail,
          trimmedUserName,
          redirectAttrs);
    }
    final UsernameValidator.Result usernameResult = UsernameValidator.validate(trimmedUserName);
    if (!usernameResult.isValid()) {
      return failed(usernameResult.getErrorMessage(), trimmedEmail, trimmedUserName, redirectAttrs);
    }
    if (!password.equals(confirmPassword)) {
      return failed("Passwords do not match.", trimmedEmail, trimmedUserName, redirectAttrs);
    }
    final PasswordValidator.Result passwordResult = PasswordValidator.validate(password);
    if (!passwordResult.isValid()) {
      return failed(passwordResult.getErrorMessage(), trimmedEmail, trimmedUserName, redirectAttrs);
    }

    // ----- Uniqueness checks -----
    if (usersRepository.findByEmail(trimmedEmail) != null) {
      return failed(
          "An account with this email already exists.",
          trimmedEmail,
          trimmedUserName,
          redirectAttrs);
    }
    if (usersRepository.findByUserName(trimmedUserName) != null) {
      return failed(
          "Username \"" + trimmedUserName + "\" is already taken.",
          trimmedEmail,
          trimmedUserName,
          redirectAttrs);
    }

    // ----- Create the user (unverified) -----
    final UserEntity user = new UserEntity();
    user.setUserName(trimmedUserName);
    user.setEmail(trimmedEmail);
    user.setPassword(passwordEncoder.encode(password));

    final UserEntity saved = usersRepository.save(user);
    saved.getRoles().add(new RoleEntity(saved.getId(), Role.USER));
    usersRepository.save(saved);

    // ----- Send verification email -----
    final String verifyBaseUrl =
        ServletUriComponentsBuilder.fromContextPath(request).path("/verify-email").toUriString();
    verificationService.sendVerification(saved, verifyBaseUrl);

    redirectAttrs.addFlashAttribute("email", trimmedEmail);
    return "redirect:/register/check-email";
  }

  @GetMapping("/register/check-email")
  public String checkEmail(Model model) {
    model.addAttribute(PageConstants.PAGE_TITLE, "Check your email | Codenza");
    return "check-email";
  }

  private String failed(
      String message, String email, String userName, RedirectAttributes redirectAttrs) {
    redirectAttrs.addFlashAttribute("error", message);
    redirectAttrs.addFlashAttribute("email", email);
    redirectAttrs.addFlashAttribute("userName", userName);
    return "redirect:/register";
  }
}
