package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.FeedbackEntity;
import com.codenzasoft.games.spring.entities.FeedbackEntity.Status;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.FeedbackRepository;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FeedbackController {

  private static final int PAGE_SIZE = 15;
  private static final Set<String> VALID_STATUSES = Set.of("NEW", "REVIEWED", "RESOLVED");

  @Autowired GamesUserDetailsService gamesUserDetailsService;
  @Autowired FeedbackRepository feedbackRepository;
  @Autowired ViewHelper viewHelper;

  // ──────────────────────────────────────────────
  //  User-facing: submit feedback
  // ──────────────────────────────────────────────

  @GetMapping("/feedback")
  public String feedbackForm(Model model, @AuthenticationPrincipal UserDetails principal) {

    final Optional<UserEntity> currentUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(principal.getUsername());

    model.addAttribute(PageConstants.PAGE_TITLE, "Submit Feedback");
    model.addAttribute(PageConstants.PAGE_USER, currentUser.orElse(null));
    model.addAttribute(PageConstants.PAGE_HELPER, viewHelper);
    return "feedback";
  }

  @PostMapping("/feedback")
  public String submitFeedback(
      @RequestParam String type,
      @RequestParam String subject,
      @RequestParam String message,
      @AuthenticationPrincipal UserDetails principal,
      RedirectAttributes redirectAttrs) {

    final Optional<UserEntity> currentUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(principal.getUsername());

    if (currentUser.isEmpty()) {
      redirectAttrs.addFlashAttribute("error", "Could not identify user.");
      return "redirect:/feedback";
    }

    if (subject == null || subject.isBlank() || message == null || message.isBlank()) {
      redirectAttrs.addFlashAttribute("error", "Subject and message are required.");
      return "redirect:/feedback";
    }

    if (subject.length() > 255) {
      redirectAttrs.addFlashAttribute("error", "Subject must be 255 characters or fewer.");
      return "redirect:/feedback";
    }

    if (message.length() > 5000) {
      redirectAttrs.addFlashAttribute("error", "Message must be 5,000 characters or fewer.");
      return "redirect:/feedback";
    }

    FeedbackEntity.Type feedbackType;
    try {
      feedbackType = FeedbackEntity.Type.valueOf(type.toUpperCase());
    } catch (IllegalArgumentException e) {
      redirectAttrs.addFlashAttribute("error", "Invalid feedback type.");
      return "redirect:/feedback";
    }

    final FeedbackEntity feedback = new FeedbackEntity();
    feedback.setUser(currentUser.get());
    feedback.setType(feedbackType);
    feedback.setSubject(subject.trim());
    feedback.setMessage(message.trim());
    feedbackRepository.save(feedback);

    redirectAttrs.addFlashAttribute("success", "Thanks! Your feedback has been submitted.");
    return "redirect:/feedback";
  }

  // ──────────────────────────────────────────────
  //  Admin: manage feedback
  // ──────────────────────────────────────────────

  @GetMapping("/admin/feedback")
  public String manageFeedback(
      Model model,
      @AuthenticationPrincipal UserDetails principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(required = false) String status) {

    final Optional<UserEntity> currentUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(principal.getUsername());

    final PageRequest pageable =
        PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));

    final Page<FeedbackEntity> feedbackPage;
    if (status != null && VALID_STATUSES.contains(status.toUpperCase())) {
      feedbackPage =
          feedbackRepository.findByStatus(Status.valueOf(status.toUpperCase()), pageable);
    } else {
      feedbackPage = feedbackRepository.findAll(pageable);
      status = null;
    }

    model.addAttribute(PageConstants.PAGE_TITLE, "Manage Feedback");
    model.addAttribute(PageConstants.PAGE_USER, currentUser.orElse(null));
    model.addAttribute(PageConstants.PAGE_HELPER, viewHelper);
    model.addAttribute("feedbackPage", feedbackPage);
    model.addAttribute("statusFilter", status);
    model.addAttribute("statuses", Status.values());
    return "admin/manage-feedback";
  }

  @PostMapping("/admin/feedback/update-status")
  public String updateStatus(
      @RequestParam UUID id,
      @RequestParam String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(required = false) String statusFilter,
      RedirectAttributes redirectAttrs) {

    feedbackRepository
        .findById(id)
        .ifPresent(
            f -> {
              try {
                f.setStatus(Status.valueOf(status.toUpperCase()));
                feedbackRepository.save(f);
              } catch (IllegalArgumentException ignored) {
              }
            });

    if (statusFilter != null) {
      redirectAttrs.addAttribute("status", statusFilter);
    }
    redirectAttrs.addAttribute("page", page);
    return "redirect:/admin/feedback";
  }

  @PostMapping("/admin/feedback/delete")
  public String deleteFeedback(
      @RequestParam(required = false) List<UUID> ids,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(required = false) String statusFilter,
      RedirectAttributes redirectAttrs) {

    if (ids == null || ids.isEmpty()) {
      redirectAttrs.addFlashAttribute("error", "No items selected.");
    } else {
      feedbackRepository.deleteAllById(ids);
      redirectAttrs.addFlashAttribute("success", ids.size() + " item(s) deleted.");
    }

    if (statusFilter != null) {
      redirectAttrs.addAttribute("status", statusFilter);
    }
    redirectAttrs.addAttribute("page", page);
    return "redirect:/admin/feedback";
  }
}
