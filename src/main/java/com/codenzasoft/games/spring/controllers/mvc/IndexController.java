package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.kh.KhGameEntity;
import com.codenzasoft.games.spring.entities.xo.XoGameEntity;
import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.views.ViewHelper;
import com.codenzasoft.games.xo.Piece;
import java.util.Map;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
      final UserEntity currentUser = optionalUser.get();
      model.addAttribute(PageConstants.PAGE_TITLE, "Codenza Games");
      model.addAttribute(PageConstants.PAGE_USER, currentUser);
      model.addAttribute(PageConstants.PAGE_HELPER, viewHelper);
      model.addAttribute("opponents", gameService.getAvailableOpponents(currentUser));
      model.addAttribute("activeGames", gameService.getActiveGames(currentUser));
      model.addAttribute("atGameLimit", gameService.isAtGameLimit(currentUser));
    }
    return "index";
  }

  @GetMapping("/games/xo/{gameId:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}")
  public String game() {
    return "forward:/xsandos.html";
  }

  @GetMapping("/games/kh/{gameId:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}")
  public String khGame() {
    return "forward:/kingshand.html";
  }

  /** Creates a new vs-bot {@link com.codenzasoft.games.xo.XoGame}. */
  @PostMapping("/games/xo")
  public String newGame(@AuthenticationPrincipal UserDetails user) {
    final Optional<UserEntity> entity =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    if (entity.isPresent()) {
      try {
        final XoGameEntity game = gameService.createXoGame(entity.get(), Piece.X, Piece.X);
        return "redirect:/games/xo/" + game.getId();
      } catch (IllegalStateException e) {
        log.warn("Game creation rejected for {}: {}", entity.get().getUsername(), e.getMessage());
      }
    }
    return "redirect:/";
  }

  /** Creates a new kh game {@link com.codenzasoft.games.kingshand.GameEngine}. */
  @PostMapping("/games/kh")
  public String newKhGame(@AuthenticationPrincipal UserDetails user) {
    final Optional<UserEntity> entity =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    if (entity.isPresent()) {
      try {
        final KhGameEntity game = gameService.createKhGame(entity.get());
        return "redirect:/games/kh/" + game.getId();
      } catch (IllegalStateException e) {
        log.warn("Game creation rejected for {}: {}", entity.get().getUsername(), e.getMessage());
      }
    }
    return "redirect:/";
  }

  /** Deletes (quits) the identified game on behalf of the authenticated user. */
  @PostMapping(
      "/games/{gameId:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}/delete")
  public String deleteGame(@AuthenticationPrincipal UserDetails user, @PathVariable UUID gameId) {
    final Optional<UserEntity> entity =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    if (entity.isPresent()) {
      try {
        gameService.quitGame(gameId, entity.get());
      } catch (IllegalStateException e) {
        log.warn("Game deletion rejected for {}: {}", entity.get().getUsername(), e.getMessage());
      }
    }
    return "redirect:/";
  }

  /** Creates a new two-player game and redirects the creator to it immediately. */
  @PostMapping("/games/xo/challenge")
  public String newChallenge(
      @AuthenticationPrincipal UserDetails user, @RequestParam UUID opponentId) {
    final Optional<UserEntity> creator =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    final Optional<UserEntity> opponent = gameService.findUser(opponentId);
    if (creator.isPresent() && opponent.isPresent()) {
      try {
        final XoGameEntity game =
            gameService.createXoGame(
                Map.of(Piece.X, creator.get(), Piece.O, opponent.get()), Piece.X);
        return "redirect:/games/xo/" + game.getId();
      } catch (IllegalStateException e) {
        log.warn(
            "Challenge creation rejected for {}: {}", creator.get().getUsername(), e.getMessage());
      }
    }
    return "redirect:/";
  }
}
