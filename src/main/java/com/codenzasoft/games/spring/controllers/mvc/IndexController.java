package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.views.ViewHelper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The {@link IndexController} serves the home (index) page and requires authentication. Its
 * corresponding template is in <code>resources/templates/index.html</code>.
 */
@Controller
public class IndexController {
  private static final Logger log = LoggerFactory.getLogger(IndexController.class);

  @Autowired GamesUserDetailsService gamesUserDetailsService;
  @Autowired ViewHelper viewHelper;

  @GetMapping("/")
  public String games(Model model, @AuthenticationPrincipal UserDetails user) {
    final Optional<UserEntity> optionalUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    if (optionalUser.isPresent()) {
      model.addAttribute(PageConstants.PAGE_TITLE, "Admin Operations");
      model.addAttribute(PageConstants.PAGE_USER, optionalUser.get());
      model.addAttribute(PageConstants.PAGE_HELPER, viewHelper);
    }
    return "index";
  }
}
