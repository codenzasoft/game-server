package com.codenzasoft.games.spring.services.hearts;

import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.PlayedCard;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a Groovy code snippet that reproduces a captured bot decision as a Spock test skeleton.
 * The output can be pasted directly into a {@code *Spec.groovy} file in game-hearts.
 */
public final class GroovySnippetGenerator {

  private GroovySnippetGenerator() {}

  public static String generate(final BotDecision d) {
    final RoundState round = d.roundState();
    final StringBuilder sb = new StringBuilder();

    header(sb, d, round);
    sb.append("def \"FIXME: describe the expected behavior\"() {\n");
    sb.append("    given:\n");
    sb.append("    def strategy = new AdaptiveStrategy()\n");
    sb.append("    def game = Mock(HeartsGame)\n\n");
    tricks(sb, round);
    hands(sb, round);
    passes(sb, round);
    roundState(sb, round);
    heartsState(sb, round);
    player(sb, d);
    whenThen(sb, d);
    sb.append("}\n");

    return sb.toString();
  }

  private static void header(final StringBuilder sb, final BotDecision d, final RoundState round) {
    sb.append("// ── Bot Decision Snapshot ────────────────────────────────────────────────────\n");
    sb.append("// Game:     ").append(d.gameId()).append("\n");
    sb.append("// Player:   ").append(d.playerName()).append("\n");
    sb.append("// Round:    ")
        .append(d.roundNumber())
        .append("  Pass: ")
        .append(round.pass())
        .append("\n");
    sb.append("// Trick:    ").append(d.trickNumber()).append("\n");
    sb.append("// Played:   ").append(card(d.cardPlayed())).append("  ← was this correct?\n");
    sb.append("// Captured: ").append(d.timestamp()).append("\n");
    sb.append("//\n");
    sb.append("// Add to imports:\n");
    sb.append("//   import static com.codenzasoft.games.common.cards.Suit.*\n");
    sb.append("//   import static com.codenzasoft.games.common.cards.Rank.*\n");
    sb.append("// Add helper: static Card c(Suit s, Rank r) { new Card(s, r) }\n");
    sb.append(
        "// ──────────────────────────────────────────────────────────────────────────────\n\n");
  }

  private static void tricks(final StringBuilder sb, final RoundState round) {
    final List<TrickState> tricks = round.tricks();
    for (int i = 0; i < tricks.size(); i++) {
      final TrickState trick = tricks.get(i);
      final String var = (i == tricks.size() - 1) ? "currentTrick" : "trick" + trick.number();
      sb.append("    def ")
          .append(var)
          .append(" = new TrickState(")
          .append(trick.number())
          .append(", \"")
          .append(trick.leader())
          .append("\", [\n");
      for (final PlayedCard pc : trick.cards()) {
        sb.append("        new PlayedCard(\"")
            .append(pc.player())
            .append("\", ")
            .append(card(pc.card()))
            .append("),\n");
      }
      sb.append("    ])\n");
    }
    if (!round.tricks().isEmpty()) sb.append("\n");
  }

  private static void hands(final StringBuilder sb, final RoundState round) {
    sb.append("    def hands = [\n");
    for (final Map.Entry<String, List<Card>> entry : round.hands().entrySet()) {
      sb.append("        \"")
          .append(entry.getKey())
          .append("\": [")
          .append(cardList(entry.getValue()))
          .append("],\n");
    }
    sb.append("    ]\n\n");
  }

  private static void passes(final StringBuilder sb, final RoundState round) {
    if (round.passes().isEmpty()) return;
    sb.append("    def passes = [\n");
    for (final Map.Entry<String, List<Card>> entry : round.passes().entrySet()) {
      sb.append("        \"")
          .append(entry.getKey())
          .append("\": [")
          .append(cardList(entry.getValue()))
          .append("],\n");
    }
    sb.append("    ]\n\n");
  }

  private static void roundState(final StringBuilder sb, final RoundState round) {
    final String passesVar = round.passes().isEmpty() ? "[:]" : "passes";
    final String tricksList = buildTricksList(round.tricks());
    sb.append("    def round = new RoundState(")
        .append(round.number())
        .append(", Pass.")
        .append(round.pass())
        .append(", \"")
        .append(round.dealer())
        .append("\",\n")
        .append("        hands, ")
        .append(passesVar)
        .append(", true,\n")
        .append("        ")
        .append(tricksList)
        .append(",\n")
        .append("        hands)\n\n");
  }

  private static void heartsState(final StringBuilder sb, final RoundState round) {
    final String playerList =
        round.hands().keySet().stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(", "));
    sb.append("    def state = new HeartsState(\n");
    sb.append("        [").append(playerList).append("],\n");
    sb.append("        [round], false, null)\n\n");
  }

  private static void player(final StringBuilder sb, final BotDecision d) {
    sb.append("    def player = new Player(\n");
    sb.append("        UUID.fromString(\"").append(d.playerUUID()).append("\"),\n");
    sb.append("        \"").append(d.playerName()).append("\")\n\n");
  }

  private static void whenThen(final StringBuilder sb, final BotDecision d) {
    sb.append("    when:\n");
    sb.append("    strategy.yourTurnToPlay(game, state, player,\n");
    sb.append("        [").append(cardList(List.copyOf(d.legalCards()))).append("] as Set)\n\n");
    sb.append("    then: \"FIXME: ")
        .append(d.playerName())
        .append(" played ")
        .append(card(d.cardPlayed()))
        .append(" — was this correct?\"\n");
    sb.append("    1 * game.playCard(player, ").append(card(d.cardPlayed())).append(")\n");
  }

  private static String buildTricksList(final List<TrickState> tricks) {
    if (tricks.isEmpty()) return "[]";
    final List<String> names = new ArrayList<>();
    for (int i = 0; i < tricks.size() - 1; i++) {
      names.add("trick" + tricks.get(i).number());
    }
    names.add("currentTrick");
    return "[" + String.join(", ", names) + "]";
  }

  private static String card(final Card c) {
    return "c(" + c.getSuit() + ", " + c.getRank() + ")";
  }

  private static String cardList(final List<Card> cards) {
    return cards.stream().map(GroovySnippetGenerator::card).collect(Collectors.joining(", "));
  }
}
