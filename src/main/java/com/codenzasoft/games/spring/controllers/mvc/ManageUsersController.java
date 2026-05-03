package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.views.ViewHelper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * The {@link ManageUsersController} serves the admin user-management page. Its corresponding
 * template is in <code>resources/templates/admin/manage-users.html</code>.
 */
@Controller
@RequestMapping("/admin")
public class ManageUsersController {

  private static final int PAGE_SIZE = 10;
  private static final Set<String> SORTABLE_FIELDS = Set.of("userName", "email", "createdAt");

  @Autowired GamesUserDetailsService gamesUserDetailsService;
  @Autowired UsersRepository usersRepository;
  @Autowired ViewHelper viewHelper;

  @GetMapping("/users")
  public String manageUsers(
      Model model,
      @AuthenticationPrincipal UserDetails principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "userName") String sort,
      @RequestParam(defaultValue = "asc") String dir) {

    final String sortField = SORTABLE_FIELDS.contains(sort) ? sort : "userName";
    final Sort.Direction direction =
        "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;

    final Page<UserEntity> usersPage =
        usersRepository.findAll(PageRequest.of(page, PAGE_SIZE, Sort.by(direction, sortField)));

    final Optional<UserEntity> currentUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(principal.getUsername());

    model.addAttribute(PageConstants.PAGE_TITLE, "Manage Users");
    model.addAttribute(PageConstants.PAGE_USER, currentUser.orElse(null));
    model.addAttribute(PageConstants.PAGE_HELPER, viewHelper);
    model.addAttribute("users", usersPage);
    model.addAttribute("sort", sortField);
    model.addAttribute("dir", direction.name().toLowerCase());
    model.addAttribute("currentUserId", currentUser.map(UserEntity::getId).orElse(null));
    return "admin/manage-users";
  }

  @PostMapping("/users/delete")
  public String deleteUsers(
      @RequestParam(required = false) List<UUID> ids,
      @AuthenticationPrincipal UserDetails principal,
      RedirectAttributes redirectAttrs) {

    if (ids == null || ids.isEmpty()) {
      redirectAttrs.addFlashAttribute("error", "No users selected.");
      return "redirect:/admin/users";
    }

    final Optional<UserEntity> currentUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(principal.getUsername());
    final UUID currentUserId = currentUser.map(UserEntity::getId).orElse(null);

    final List<UUID> toDelete = ids.stream().filter(id -> !id.equals(currentUserId)).toList();
    usersRepository.deleteAllById(toDelete);

    redirectAttrs.addFlashAttribute("success", toDelete.size() + " user(s) deleted.");
    return "redirect:/admin/users";
  }
}
