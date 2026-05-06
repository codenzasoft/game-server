package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.xo.XoGameEntity;
import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.views.ViewHelper;
import com.codenzasoft.games.xo.Piece;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * The {@link IndexController} serves the home (index) page and requires authentication. Its
 * corresponding template is in <code>resources/templates/index.html</code>.
 */
@Controller
public class IndexController {
  private static final Logger log = LoggerFactory.getLogger(IndexController.class);

  @Autowired GamesUserDetailsService gamesUserDetailsService;
  @Autowired ViewHelper viewHelper;
  @Autowired GameService gameService;

  @GetMapping("/")
  public String games(Model model, @AuthenticationPrincipal UserDetails user) {
    final Optional<UserEntity> optionalUser =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    if (optionalUser.isPresent()) {
      model.addAttribute(PageConstants.PAGE_TITLE, "Codenza Games");
      model.addAttribute(PageConstants.PAGE_USER, optionalUser.get());
      model.addAttribute(PageConstants.PAGE_HELPER, viewHelper);
    }
    return "index";
  }

  /**
   * Creates a new single user {@link com.codenzasoft.games.xo.XoGame}.
   *
   * @param user The game player.
   * @return A redirect to the new game.
   */
  @PostMapping("/games/xo")
  public String newGame(@AuthenticationPrincipal UserDetails user) {
    // TODO: limit new game creation - avoid attacks
    final Optional<UserEntity> entity =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    if (entity.isPresent()) {
      final XoGameEntity game =
          gameService.createXoGame(UUID.randomUUID(), entity.get(), Piece.X, Piece.X);
      final URI uri = viewHelper.getGameURI(game);
      return "redirect:" + uri.toString();
    }

    return "redirect:/";
  }
}
