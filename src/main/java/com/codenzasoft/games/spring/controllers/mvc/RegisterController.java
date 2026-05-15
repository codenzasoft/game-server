package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.RoleEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.WhitelistEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.repositories.WhitelistRepository;
import com.codenzasoft.games.spring.validation.PasswordValidator;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles self-service account creation. Registration is gated by the whitelist: only emails that
 * an admin has whitelisted can register. The role granted to the new user is whatever role the
 * admin set on the whitelist entry. The whitelist entry is deleted on successful registration so
 * each entry is single-use.
 */
@Controller
public class RegisterController {

  private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

  @Autowired UsersRepository usersRepository;
  @Autowired WhitelistRepository whitelistRepository;
  @Autowired PasswordEncoder passwordEncoder;

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
    if (!password.equals(confirmPassword)) {
      return failed("Passwords do not match.", trimmedEmail, trimmedUserName, redirectAttrs);
    }
    final PasswordValidator.Result passwordResult = PasswordValidator.validate(password);
    if (!passwordResult.isValid()) {
      return failed(passwordResult.getErrorMessage(), trimmedEmail, trimmedUserName, redirectAttrs);
    }

    // ----- Whitelist check -----
    final WhitelistEntity whitelistEntry = whitelistRepository.findByEmailIgnoreCase(trimmedEmail);
    if (whitelistEntry == null) {
      // Don't reveal whether the email is whitelisted; treat all rejections the same. The admin
      // controls who can register, so a non-whitelisted user being told "contact your admin" is
      // the right UX.
      return failed(
          "This email is not authorized to create an account. "
              + "Please contact an administrator.",
          trimmedEmail,
          trimmedUserName,
          redirectAttrs);
    }

    // ----- Uniqueness checks against existing users -----
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

    // ----- Create the user -----
    final UserEntity user = new UserEntity();
    user.setUserName(trimmedUserName);
    user.setEmail(trimmedEmail);
    // DelegatingPasswordEncoder.encode() already returns a prefixed string like "{bcrypt}...".
    user.setPassword(passwordEncoder.encode(password));

    final UserEntity saved = usersRepository.save(user);
    saved.getRoles().add(new RoleEntity(saved.getId(), whitelistEntry.getRole()));
    usersRepository.save(saved);

    // Consume the whitelist entry.
    whitelistRepository.deleteByEmailIgnoreCase(trimmedEmail);

    redirectAttrs.addFlashAttribute(
        "success", "Account created. You can now sign in with your username or email.");
    return "redirect:/login";
  }

  private String failed(
      String message, String email, String userName, RedirectAttributes redirectAttrs) {
    redirectAttrs.addFlashAttribute("error", message);
    redirectAttrs.addFlashAttribute("email", email);
    redirectAttrs.addFlashAttribute("userName", userName);
    return "redirect:/register";
  }
}
