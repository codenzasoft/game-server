package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.kh.KhGameResultRepository;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.views.ViewHelper;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/kh")
public class KhLeaderboardController {

  @Autowired GamesUserDetailsService gamesUserDetailsService;
  @Autowired KhGameResultRepository khGameResultRepository;
  @Autowired ViewHelper viewHelper;

  @GetMapping("/leaderboard")
  public String leaderboard(
      Model model,
      @AuthenticationPrincipal UserDetails principal,
      @RequestParam(defaultValue = "duration") String tab) {

    final Optional<UserEntity> currentUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(principal.getUsername());

    model.addAttribute(PageConstants.PAGE_TITLE, "King's Hand Leaderboard");
    model.addAttribute(PageConstants.PAGE_USER, currentUser.orElse(null));
    model.addAttribute(PageConstants.PAGE_HELPER, viewHelper);
    model.addAttribute(
        "topByDuration", khGameResultRepository.findTop5ByOrderByYearSurvivedDescSeasonOrderDesc());
    model.addAttribute(
        "topByPopulation", khGameResultRepository.findTop5ByOrderByPeakPopulationDesc());
    model.addAttribute("activeTab", tab);
    return "kh/leaderboard";
  }
}
