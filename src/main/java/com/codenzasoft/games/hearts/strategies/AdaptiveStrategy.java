package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A Hearts bot that selects a mode at the start of each round and adapts its play accordingly.
 *
 * <ul>
 *   <li>{@link Mode#OFFENSIVE} — attempts to shoot the moon; committed when {@link
 *       HandEvaluator#isShootViable} is true for the dealt hand. Reverts to DEFENSIVE the moment an
 *       opponent wins a point trick.
 *   <li>{@link Mode#SABOTEUR} — not yet active (Stage 3).
 *   <li>{@link Mode#DEFENSIVE} — delegates all play to {@link DefensiveStrategy}.
 * </ul>
 */
public class AdaptiveStrategy extends DefensiveStrategy {

  private int evaluatedRound = -1;
  private boolean committedToShoot = false;

  public AdaptiveStrategy() {}

  /** Test constructor: pre-seeds shooting commitment without requiring a game to be played. */
  AdaptiveStrategy(final boolean committedToShoot) {
    this.committedToShoot = committedToShoot;
    this.evaluatedRound = Integer.MAX_VALUE;
  }

  // ── HeartsPlayerObserver ────────────────────────────────────────────────────

  @Override
  public void yourTurnToPlay(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final Set<Card> validCards) {
    evaluateRoundIfNeeded(state, player);
    if (resolveMode(state, player) == Mode.OFFENSIVE) {
      selectOffensiveCard(state, player, validCards).ifPresent(card -> game.playCard(player, card));
    } else {
      super.yourTurnToPlay(game, state, player, validCards);
    }
  }

  @Override
  public void yourTurnToPass(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> hand) {
    evaluateRoundIfNeeded(state, player);
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

    // No hearts available — lead a suit where I hold the highest remaining card
    for (final Card candidate :
        validCards.stream().sorted(Comparator.comparing(Card::getRank).reversed()).toList()) {
      final Suit suit = candidate.getSuit();
      final boolean isHighest =
          StrategyHelper.filterSuit(round.getRemainingCards(), suit)
              .filter(c -> !round.getHand(player).contains(c))
              .noneMatch(c -> c.outRanks(candidate));
      if (isHighest) return Optional.of(candidate);
    }

    return validCards.stream().max(Comparator.comparing(Card::getRank));
  }

  private Optional<Card> selectOffensiveSluff(final Set<Card> validCards) {
    // Discard a low safe card — preserve hearts and Q♠ for the moon shot
    final Optional<Card> safeLow =
        validCards.stream()
            .filter(c -> c.getSuit() != Suit.HEARTS)
            .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
            .min(Comparator.comparing(Card::getRank));
    if (safeLow.isPresent()) return safeLow;
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

  // ── Internal ────────────────────────────────────────────────────────────────

  private void evaluateRoundIfNeeded(final HeartsState state, final Player player) {
    final int roundNumber = state.getRound().number();
    if (evaluatedRound != roundNumber) {
      evaluatedRound = roundNumber;
      committedToShoot = HandEvaluator.isShootViable(state.getRound().getHand(player));
    }
  }
}
