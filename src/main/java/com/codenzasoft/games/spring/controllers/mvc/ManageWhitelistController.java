package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.WhitelistEntity;
import com.codenzasoft.games.spring.repositories.WhitelistRepository;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.views.ViewHelper;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The {@link ManageWhitelistController} serves the admin whitelist-management page. Its
 * corresponding template is in <code>resources/templates/admin/manage-whitelist.html</code>.
 */
@Controller
@RequestMapping("/admin")
public class ManageWhitelistController {

  private static final int PAGE_SIZE = 10;
  private static final Set<String> SORTABLE_FIELDS = Set.of("email", "role", "createdAt");

  /** Roles an admin can assign via the whitelist UI. BOT accounts are provisioned elsewhere. */
  private static final List<Role> ASSIGNABLE_ROLES =
      Arrays.stream(Role.values()).filter(r -> r != Role.BOT).toList();

  @Autowired GamesUserDetailsService gamesUserDetailsService;
  @Autowired WhitelistRepository whitelistRepository;
  @Autowired ViewHelper viewHelper;

  @GetMapping("/whitelist")
  public String manageWhitelist(
      Model model,
      @AuthenticationPrincipal UserDetails principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "email") String sort,
      @RequestParam(defaultValue = "asc") String dir) {

    final String sortField = SORTABLE_FIELDS.contains(sort) ? sort : "email";
    final Sort.Direction direction =
        "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;

    final Page<WhitelistEntity> entriesPage =
        whitelistRepository.findAll(PageRequest.of(page, PAGE_SIZE, Sort.by(direction, sortField)));

    final Optional<UserEntity> currentUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(principal.getUsername());

    model.addAttribute(PageConstants.PAGE_TITLE, "Manage Whitelist");
    model.addAttribute(PageConstants.PAGE_USER, currentUser.orElse(null));
    model.addAttribute(PageConstants.PAGE_HELPER, viewHelper);
    model.addAttribute("entries", entriesPage);
    model.addAttribute("sort", sortField);
    model.addAttribute("dir", direction.name().toLowerCase());
    model.addAttribute("roles", ASSIGNABLE_ROLES);
    return "admin/manage-whitelist";
  }

  @PostMapping("/whitelist/add")
  public String addEntry(
      @RequestParam String email, @RequestParam String role, RedirectAttributes redirectAttrs) {

    final String trimmed = email == null ? "" : email.trim();
    if (trimmed.isEmpty()) {
      redirectAttrs.addFlashAttribute("error", "Email is required.");
      return "redirect:/admin/whitelist";
    }
    if (!trimmed.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      redirectAttrs.addFlashAttribute("error", "\"" + trimmed + "\" is not a valid email.");
      return "redirect:/admin/whitelist";
    }

    final Role parsedRole = Role.fromString(role);
    if (parsedRole == null || !ASSIGNABLE_ROLES.contains(parsedRole)) {
      redirectAttrs.addFlashAttribute("error", "Unknown or non-assignable role: " + role);
      return "redirect:/admin/whitelist";
    }

    final String normalized = trimmed.toLowerCase();
    if (whitelistRepository.existsByEmailIgnoreCase(normalized)) {
      redirectAttrs.addFlashAttribute("error", normalized + " is already whitelisted.");
      return "redirect:/admin/whitelist";
    }

    whitelistRepository.save(new WhitelistEntity(normalized, parsedRole));
    redirectAttrs.addFlashAttribute("success", normalized + " added to the whitelist.");
    return "redirect:/admin/whitelist";
  }

  @PostMapping("/whitelist/remove")
  @Transactional
  public String removeEntries(
      @RequestParam(required = false) List<String> emails, RedirectAttributes redirectAttrs) {

    if (emails == null || emails.isEmpty()) {
      redirectAttrs.addFlashAttribute("error", "No entries selected.");
      return "redirect:/admin/whitelist";
    }

    long removed = 0;
    for (String email : emails) {
      removed += whitelistRepository.deleteByEmailIgnoreCase(email);
    }

    redirectAttrs.addFlashAttribute("success", removed + " entry/entries removed.");
    return "redirect:/admin/whitelist";
  }
}
