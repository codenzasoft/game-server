package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.xo.XoGameEntity;
import com.codenzasoft.games.spring.services.GameService;
import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import com.codenzasoft.games.spring.views.ViewHelper;
import com.codenzasoft.games.xo.Piece;
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
      model.addAttribute("outgoingInvites", gameService.getPendingOutgoingInvites(currentUser));
      model.addAttribute("incomingInvites", gameService.getPendingIncomingInvites(currentUser));
      model.addAttribute("activeGames", gameService.getActiveGames(currentUser));
    }
    return "index";
  }

  @GetMapping("/games/xo/{gameId:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}")
  public String game() {
    return "forward:/index.html";
  }

  /** Creates a new vs-bot {@link com.codenzasoft.games.xo.XoGame}. */
  @PostMapping("/games/xo")
  public String newGame(@AuthenticationPrincipal UserDetails user) {
    final Optional<UserEntity> entity =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    if (entity.isPresent()) {
      final XoGameEntity game =
          gameService.createXoGame(UUID.randomUUID(), entity.get(), Piece.X, Piece.X);
      return "redirect:/games/xo/" + game.getId();
    }
    return "redirect:/";
  }

  /** Sends a game invite to the selected opponent. */
  @PostMapping("/games/xo/invite")
  public String newInvite(
      @AuthenticationPrincipal UserDetails user, @RequestParam UUID opponentId) {
    final Optional<UserEntity> creator =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    final Optional<UserEntity> invitee = gameService.findUser(opponentId);
    if (creator.isPresent() && invitee.isPresent()) {
      gameService.createXoInvite(UUID.randomUUID(), creator.get(), invitee.get());
    }
    return "redirect:/";
  }

  /** Accepts a pending game invite and redirects to the game. */
  @PostMapping("/games/xo/{gameId}/accept")
  public String acceptInvite(@PathVariable UUID gameId, @AuthenticationPrincipal UserDetails user) {
    final Optional<UserEntity> actor =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    if (actor.isPresent()) {
      gameService.acceptInvite(gameId, actor.get());
      return "redirect:/games/xo/" + gameId;
    }
    return "redirect:/";
  }

  /** Declines a pending game invite. */
  @PostMapping("/games/xo/{gameId}/decline")
  public String declineInvite(
      @PathVariable UUID gameId, @AuthenticationPrincipal UserDetails user) {
    final Optional<UserEntity> actor =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    actor.ifPresent(u -> gameService.declineInvite(gameId, u));
    return "redirect:/";
  }

  /** Cancels an outgoing pending game invite. */
  @PostMapping("/games/xo/{gameId}/cancel")
  public String cancelInvite(@PathVariable UUID gameId, @AuthenticationPrincipal UserDetails user) {
    final Optional<UserEntity> actor =
        gamesUserDetailsService.getUserByUsernameOrEmail(user.getUsername());
    actor.ifPresent(u -> gameService.cancelInvite(gameId, u));
    return "redirect:/";
  }
}
