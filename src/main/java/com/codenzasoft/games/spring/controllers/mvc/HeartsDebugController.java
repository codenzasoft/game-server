package com.codenzasoft.games.spring.controllers.mvc;

import com.codenzasoft.games.spring.services.hearts.BotDecision;
import com.codenzasoft.games.spring.services.hearts.BotDecisionLogService;
import com.codenzasoft.games.spring.services.hearts.GroovySnippetGenerator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Admin endpoint that returns Groovy test snippets for recent bot decisions in a Hearts game.
 * Access requires the {@code ADMIN} role (enforced by {@code SecurityConfig}).
 *
 * <p>{@code GET /admin/hearts/{gameId}/bot-decisions} returns plain-text Groovy snippets, one per
 * captured decision (newest first), ready to paste into a Spock spec in game-hearts.
 */
@Controller
@RequestMapping("/admin/hearts")
public class HeartsDebugController {

  @Autowired private BotDecisionLogService botDecisionLogService;

  @GetMapping(value = "/{gameId}/bot-decisions", produces = MediaType.TEXT_PLAIN_VALUE)
  @ResponseBody
  public String botDecisions(@PathVariable final UUID gameId) {
    final List<BotDecision> decisions = botDecisionLogService.getDecisions(gameId);
    if (decisions.isEmpty()) {
      return "// No bot decisions recorded for game " + gameId + "\n";
    }

    final StringBuilder sb = new StringBuilder();
    final List<BotDecision> newest = decisions.reversed();
    for (int i = 0; i < newest.size(); i++) {
      if (i > 0) sb.append("\n\n");
      sb.append(GroovySnippetGenerator.generate(newest.get(i)));
    }
    return sb.toString();
  }
}
