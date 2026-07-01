package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A Hearts bot that selects a mode at the start of each round and adapts its play accordingly.
 *
 * <ul>
 *   <li>{@link Mode#OFFENSIVE} — attempts to shoot the moon; committed when {@link
 *       HandEvaluator#isShootViable} is true for the dealt hand. Reverts to DEFENSIVE the moment an
 *       opponent wins a point trick.
 *   <li>{@link Mode#SABOTEUR} — detects when an opponent has reached the moon-shot threat threshold
 *       ({@link RoundContext#SHOOT_THREAT_THRESHOLD}) and blocks them: beats their winning tricks
 *       when possible; avoids giving them more point cards when unable to beat. Falls back to
 *       {@link DefensiveStrategy} when the shooter is not currently winning the trick.
 *   <li>{@link Mode#DEFENSIVE} — delegates all play to {@link DefensiveStrategy}.
 * </ul>
 */
public class AdaptiveStrategy extends DefensiveStrategy {

  private int evaluatedPassRound = -1;
  private int evaluatedPlayRound = -1;
  private boolean committedToShoot = false;

  public AdaptiveStrategy() {}

  /** Test constructor: pre-seeds shooting commitment without requiring a game to be played. */
  AdaptiveStrategy(final boolean committedToShoot) {
    this.committedToShoot = committedToShoot;
    this.evaluatedPassRound = Integer.MAX_VALUE;
    this.evaluatedPlayRound = Integer.MAX_VALUE;
  }

  // ── HeartsPlayerObserver ────────────────────────────────────────────────────

  @Override
  public void yourTurnToPlay(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final Set<Card> validCards) {
    evaluateForPlay(state, player);
    if (committedToShoot && RoundContext.hasMoonShotFailed(state, player.name())) {
      final Optional<Card> bail = selectBailOutCard(state.getRound(), validCards);
      if (bail.isPresent()) {
        game.playCard(player, bail.get());
      } else {
        super.yourTurnToPlay(game, state, player, validCards);
      }
      return;
    }
    final Mode mode = resolveMode(state, player);
    if (mode == Mode.OFFENSIVE) {
      selectOffensiveCard(state, player, validCards).ifPresent(card -> game.playCard(player, card));
    } else if (mode == Mode.SABOTEUR) {
      final String shooter = RoundContext.shootingPlayer(state).orElseThrow();
      final Optional<Card> card = selectSaboteurCard(state, player, validCards, shooter);
      if (card.isPresent()) {
        game.playCard(player, card.get());
      } else {
        super.yourTurnToPlay(game, state, player, validCards);
      }
    } else {
      super.yourTurnToPlay(game, state, player, validCards);
    }
  }

  @Override
  public void yourTurnToPass(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> hand) {
    evaluateForPass(state, player);
    if (committedToShoot) {
      game.setPass(player, selectOffensivePassCards(state.getRound(), player));
    } else {
      super.yourTurnToPass(game, state, player, hand);
    }
  }

  // ── Mode resolution ─────────────────────────────────────────────────────────

  Mode resolveMode(final HeartsState state, final Player player) {
    if (committedToShoot && !RoundContext.hasMoonShotFailed(state, player.name())) {
      return Mode.OFFENSIVE;
    }
    final Optional<String> shooter = RoundContext.shootingPlayer(state);
    if (shooter.isPresent() && !shooter.get().equals(player.name())) {
      return Mode.SABOTEUR;
    }
    return Mode.DEFENSIVE;
  }

  // ── Offensive card selection ─────────────────────────────────────────────────

  Optional<Card> selectOffensiveCard(
      final HeartsState state, final Player player, final Set<Card> validCards) {
    if (validCards.isEmpty()) return Optional.empty();
    if (validCards.size() == 1) return validCards.stream().findFirst();

    final RoundState round = state.getRound();
    final Optional<Suit> maybeTrump = round.getTrick().getTrump();

    if (maybeTrump.isEmpty()) {
      return selectOffensiveLeadCard(round, player, validCards);
    }

    final Suit trump = maybeTrump.get();
    if (StrategyHelper.filterSuit(validCards, trump).findAny().isPresent()) {
      // Follow suit — always play highest to win the trick
      return StrategyHelper.getMaxRank(validCards, trump);
    }
    return selectOffensiveSluff(validCards);
  }

  private Optional<Card> selectOffensiveLeadCard(
      final RoundState round, final Player player, final Set<Card> validCards) {
    // Lead highest heart to collect points aggressively
    final Optional<Card> highHeart = StrategyHelper.getMaxRank(validCards, Suit.HEARTS);
    if (highHeart.isPresent()) return highHeart;

    // No hearts available — lead a suit where I hold the highest remaining card (never Q♠)
    for (final Card candidate :
        validCards.stream()
            .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
            .sorted(Comparator.comparing(Card::getRank).reversed())
            .toList()) {
      final Suit suit = candidate.getSuit();
      final boolean isHighest =
          StrategyHelper.filterSuit(round.getRemainingCards(), suit)
              .filter(c -> !round.getHand(player).contains(c))
              .noneMatch(c -> c.outRanks(candidate));
      if (isHighest) return Optional.of(candidate);
    }

    // No guaranteed-win card exists — lead highest non-Q♠ card to avoid handing away 13 pts
    return validCards.stream()
        .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
        .max(Comparator.comparing(Card::getRank))
        .or(() -> validCards.stream().max(Comparator.comparing(Card::getRank)));
  }

  private Optional<Card> selectOffensiveSluff(final Set<Card> validCards) {
    // Discard a low safe card — preserve hearts, Q♠, and A♠/K♠ (spade protection) for the shot
    final Optional<Card> safeLow =
        validCards.stream()
            .filter(c -> c.getSuit() != Suit.HEARTS)
            .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
            .filter(c -> !(c.getSuit() == Suit.SPADES && c.outRanks(Rank.QUEEN)))
            .min(Comparator.comparing(Card::getRank));
    if (safeLow.isPresent()) return safeLow;
    // Fall back to A♠/K♠ only when no other non-point option exists
    final Optional<Card> spadeProtector =
        validCards.stream()
            .filter(c -> c.getSuit() != Suit.HEARTS)
            .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
            .min(Comparator.comparing(Card::getRank));
    if (spadeProtector.isPresent()) return spadeProtector;
    // All cards are hearts / Q♠ — nothing safe to discard, play lowest
    return validCards.stream().min(Comparator.comparing(Card::getRank));
  }

  // ── Offensive pass selection ─────────────────────────────────────────────────

  List<Card> selectOffensivePassCards(final RoundState round, final Player player) {
    final List<Card> hand = round.getHand(player);

    final List<Card> passable =
        hand.stream()
            .filter(c -> !isOffensiveKeepCard(c))
            .sorted(Comparator.comparing(Card::getRank))
            .collect(Collectors.toList());

    if (passable.size() >= 3) {
      return passable.subList(0, 3);
    }

    // Not enough passable cards — supplement with the least-valuable protected cards
    final List<Card> combined = new ArrayList<>(passable);
    hand.stream()
        .filter(this::isOffensiveKeepCard)
        .sorted(Comparator.comparing(Card::getRank))
        .forEach(combined::add);
    return combined.subList(0, 3);
  }

  private boolean isOffensiveKeepCard(final Card card) {
    if (card.getSuit() == Suit.HEARTS && card.outRanks(Rank.EIGHT)) return true; // 9♥–A♥
    if (card.equals(HeartsGame.QUEEN_OF_SPADES)) return true;
    if (card.getSuit() == Suit.SPADES && card.outRanks(Rank.QUEEN)) return true; // A♠, K♠
    if (card.getRank() == Rank.ACE) return true; // A♣, A♦
    return false;
  }

  // ── Saboteur card selection ──────────────────────────────────────────────────

  Optional<Card> selectSaboteurCard(
      final HeartsState state,
      final Player player,
      final Set<Card> validCards,
      final String shooter) {
    if (validCards.isEmpty()) return Optional.empty();
    if (validCards.size() == 1) return validCards.stream().findFirst();

    final RoundState round = state.getRound();
    final TrickState trick = round.getTrick();
    final Optional<Suit> maybeTrump = trick.getTrump();

    if (maybeTrump.isEmpty()) {
      return selectSaboteurLeadCard(round, player, validCards);
    }

    final Suit trump = maybeTrump.get();
    final Optional<Card> currentWinner = StrategyHelper.getMaxTrump(trick);
    final boolean shooterIsWinning =
        currentWinner
            .flatMap(max -> trick.cards().stream().filter(pc -> pc.card().equals(max)).findFirst())
            .map(pc -> pc.player().equals(shooter))
            .orElse(false);

    if (!shooterIsWinning) {
      final boolean shooterHasPlayed =
          trick.cards().stream().anyMatch(pc -> pc.player().equals(shooter));
      if (shooterHasPlayed || StrategyHelper.getPoints(trick) == 0) {
        return Optional.empty(); // safe to defer — no points at risk or shooter already played
      }
      // Points are in the trick and the shooter hasn't played yet — steal it preemptively
      if (StrategyHelper.filterSuit(validCards, trump).findAny().isPresent()) {
        return StrategyHelper.getMaxRank(validCards, trump);
      }
      return selectSaboteurSafeSluff(validCards);
    }

    if (StrategyHelper.filterSuit(validCards, trump).findAny().isPresent()) {
      // Must follow suit — try to beat the shooter with the highest available card
      final Optional<Card> beater =
          currentWinner.flatMap(
              max ->
                  StrategyHelper.filterSuit(validCards, trump)
                      .filter(c -> c.outRanks(max.getRank()))
                      .max(Comparator.comparing(Card::getRank)));
      if (beater.isPresent()) return beater;
      // Can't beat — play lowest to minimise points given to the shooter
      return StrategyHelper.getMinRank(validCards, trump);
    }

    // Void in trump — must sluff; avoid giving the shooter more point cards
    return selectSaboteurSafeSluff(validCards);
  }

  private Optional<Card> selectSaboteurSafeSluff(final Set<Card> validCards) {
    final Optional<Card> safeCard =
        validCards.stream()
            .filter(c -> c.getSuit() != Suit.HEARTS)
            .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
            .min(Comparator.comparing(Card::getRank));
    if (safeCard.isPresent()) return safeCard;
    return validCards.stream().min(Comparator.comparing(Card::getRank));
  }

  // ── Saboteur lead ────────────────────────────────────────────────────────────

  private Optional<Card> selectSaboteurLeadCard(
      final RoundState round, final Player player, final Set<Card> validCards) {
    final List<Card> myHand = round.getHand(player);
    // Lead the highest non-hearts card we're guaranteed to win (no opponent holds a higher card
    // of that suit), so the saboteur takes the trick and the shooter doesn't.
    return validCards.stream()
        .filter(c -> c.getSuit() != Suit.HEARTS)
        .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
        .filter(
            c ->
                StrategyHelper.filterSuit(round.getRemainingCards(), c.getSuit())
                    .filter(r -> !myHand.contains(r))
                    .noneMatch(r -> r.outRanks(c)))
        .max(Comparator.comparing(Card::getRank));
  }

  // ── Bail-out ─────────────────────────────────────────────────────────────────

  private Optional<Card> selectBailOutCard(final RoundState round, final Set<Card> validCards) {
    if (round.getTrick().getTrump().isPresent()) {
      return Optional.empty(); // not leading — defensive follow/sluff handles this correctly
    }
    // Leading after a failed moon shot — dump the highest heart to shed dangerous cards
    return StrategyHelper.getMaxRank(validCards, Suit.HEARTS);
  }

  // ── Internal ────────────────────────────────────────────────────────────────

  private void evaluateForPass(final HeartsState state, final Player player) {
    final int roundNumber = state.getRound().number();
    if (evaluatedPassRound != roundNumber) {
      evaluatedPassRound = roundNumber;
      committedToShoot = HandEvaluator.isShootViable(state.getRound().getHand(player));
    }
  }

  private void evaluateForPlay(final HeartsState state, final Player player) {
    final int roundNumber = state.getRound().number();
    if (evaluatedPlayRound != roundNumber) {
      evaluatedPlayRound = roundNumber;
      // Re-evaluate with the post-pass hand; received cards may change viability.
      committedToShoot = HandEvaluator.isShootViable(state.getRound().getHand(player));
    }
  }
}
