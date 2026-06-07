package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.*;
import java.util.*;
import java.util.stream.Collectors;

public class DefensiveStrategy implements HeartsPlayerObserver {

  @Override
  public void yourTurnToPlay(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final Set<Card> validCards) {
    final Optional<Card> maybeCard = selectNextCard(state, player, validCards);
    maybeCard.ifPresent(card -> game.playCard(player, card));
  }

  @Override
  public void yourTurnToPass(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> hand) {
    game.setPass(player, selectCardsToPass(state.getRound(), player, state.getRound().pass()));
  }

  private Optional<Card> selectNextCard(
      final HeartsState state, final Player player, final Set<Card> validCards) {
    if (validCards.isEmpty()) {
      return Optional.empty();
    }
    if (validCards.size() == 1) {
      // no choice
      return validCards.stream().findFirst();
    }

    final RoundState round = state.getRound();
    final Optional<Suit> maybeTrump = round.getTrick().getTrump();
    if (maybeTrump.isEmpty()) {
      // there is no trump yet - I am leading
      return selectLeadCard(round, player, validCards);
    } else {
      // non-lead play
      final Suit trump = maybeTrump.get();
      if (StrategyHelper.filterSuit(validCards, trump).findAny().isPresent()) {
        // follow trump
        if (trump == Suit.SPADES) {
          return followSpades(state, round, player, validCards, trump);
        } else {
          return followTrump(round, player, validCards, trump);
        }
      } else {
        // sluff a card
        return selectNextSluffCard(round, player, validCards);
      }
    }
  }

  private Optional<Card> followTrump(
      final RoundState round, final Player player, final Set<Card> legalCards, final Suit trump) {
    if (round.number() == 1) {
      // play the highest trump, it's safe on the first trick
      return StrategyHelper.getMaxRank(legalCards, trump);
    }

    final TrickState trick = round.getTrick();
    // if it's te last card in the trick and there are no points, take it with a high card
    if (trick.cards().size() == 3 && StrategyHelper.getPoints(trick) == 0) {
      // no points in the trick, so take it with the highest card
      return StrategyHelper.getMaxRank(legalCards, trump);
    }

    // attempt to play a lower card
    final Optional<Card> lower =
        StrategyHelper.getMaxLessThan(legalCards, StrategyHelper.getMaxTrump(trick).get());
    // play the lower card or else play the highest trump
    return lower.or(() -> StrategyHelper.getMaxRank(legalCards, trump));
  }

  private Optional<Card> followSpades(
      final HeartsState hearts,
      final RoundState round,
      final Player player,
      final Set<Card> legalCards,
      final Suit trump) {
    if (StrategyHelper.hasQueenBeenPlayed(round)) {
      // just like any other suit if the queen has already been played
      return followTrump(round, player, legalCards, trump);
    }

    final TrickState trick = round.getTrick();
    if (StrategyHelper.hasQueenBeenPlayed(trick)) {
      // avoid taking the queen by playing the highest spade that won't take the queen
      final Optional<Card> belowQueen =
          StrategyHelper.getMaxLessThan(legalCards, HeartsGame.QUEEN_OF_SPADES);
      // or must play a remaining spade
      return belowQueen.or(() -> Optional.of(legalCards.iterator().next()));
    }
    if (legalCards.contains(HeartsGame.QUEEN_OF_SPADES)) {
      // I have the queen. If a higher spade has been played, dump the queen.
      if (StrategyHelper.getMaxTrump(trick).map(c -> c.outRanks(Rank.QUEEN)).orElse(false)) {
        return Optional.of(HeartsGame.QUEEN_OF_SPADES);
      }
      // If the only remaining spades are king and ace, and I don't have them all, play the queen
      // (and this is not the last card)
      if (isSafeToPlayQueenOfSpades(round, player)) {
        return Optional.of(HeartsGame.QUEEN_OF_SPADES);
      }
      // Otherwise, play a spade that is not the queen (if I can).
      final Optional<Card> nonQueen =
          legalCards.stream()
              .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
              .min(Comparator.comparing(Card::getRank));
      return nonQueen.or(() -> Optional.of(HeartsGame.QUEEN_OF_SPADES));
    }

    if (trick.cards().size() == 3
        || isQueenPassedToOpponentPlayingBefore(hearts, round, player, trick)) {
      // play my highest spade since the queen is before me and was not played
      return StrategyHelper.getMaxRank(legalCards, trump);
    }

    Optional<Card> lower = StrategyHelper.getMaxLessThan(legalCards, HeartsGame.QUEEN_OF_SPADES);
    return lower.or(() -> StrategyHelper.getMaxRank(legalCards, trump));
  }

  private boolean isQueenPassedToOpponentPlayingBefore(
      final HeartsState hearts,
      final RoundState round,
      final Player player,
      final TrickState trick) {
    if (round.pass() != Pass.KEEPER
        && round.getPass(player.name()).contains(HeartsGame.QUEEN_OF_SPADES)) {
      final String passedToPlayer = hearts.getPlayerRelativeTo(player.name(), round.pass());
      return trick.cards().stream().map(PlayedCard::player).toList().contains(passedToPlayer);
    }
    return false;
  }

  /**
   * Returns whether it's safe to play the queen of spades. It's safe when another player will have
   * to play the king or ace.
   *
   * @param round A {@link RoundState}
   * @param player The {@link Player} for which safety is being determined
   * @return Whether it's safe to play the queen of spades
   */
  private boolean isSafeToPlayQueenOfSpades(final RoundState round, final Player player) {
    if ((round.getTrick().cards().size() < 3)
        && round.getHand(player).contains(HeartsGame.QUEEN_OF_SPADES)) {
      final Set<Card> playerSpades =
          StrategyHelper.getCardsOfSuit(round.getHand(player), Suit.SPADES);
      final Set<Card> otherSpades =
          new HashSet<>(StrategyHelper.getCardsOfSuit(round.getRemainingCards(), Suit.SPADES));
      otherSpades.removeAll(playerSpades);
      return !otherSpades.isEmpty() && otherSpades.stream().allMatch(c -> c.outRanks(Rank.JACK));
    }
    return false;
  }

  private Optional<Card> selectNextSluffCard(
      final RoundState round, final Player player, final Set<Card> legalCards) {
    // sluff the queen, king, or ace of spades (in that order)
    final Optional<Card> highSpade =
        legalCards.stream()
            .filter(c -> c.getSuit().equals(Suit.SPADES))
            .filter(c -> c.outRanks(Rank.JACK))
            .min(Comparator.comparing(Card::getRank));
    if (highSpade.isPresent()) {
      return highSpade;
    }
    // sluff a high heart or a high card
    final Optional<Card> highHeart = StrategyHelper.getMaxRank(legalCards, Suit.HEARTS);
    return highHeart.or(() -> legalCards.stream().max(Comparator.comparing(Card::getRank)));
  }

  protected Optional<Card> selectLeadCard(
      final RoundState round, final Player player, final Set<Card> legalCards) {
    // no choice - handles leading a round with 2 of clubs
    if (legalCards.size() == 1) {
      return Optional.of(legalCards.iterator().next());
    }

    // Considerations if I have the queen of spades:
    // * lead the queen if only other remaining spades are higher
    // * lead a short suit to create a void to dump the queen
    // * lead a spade if I have enough to protection, to draw out remaining spades, such
    //   that I cannot be led into.
    //
    // Considerations if I do not have the queen of spades or higher:
    // * lead a lower spade to force out the queen

    // try to weed out the Queen
    if (!StrategyHelper.hasQueenBeenPlayed(round)) {
      if (StrategyHelper.filterSuit(legalCards, Suit.SPADES).findAny().isPresent()) {
        // lead a spade, if I don't have the Q, K, A
        final List<Card> trouble =
            StrategyHelper.getHigherCards(legalCards, Suit.SPADES, Rank.JACK);
        if (trouble.isEmpty()) {
          return StrategyHelper.getMaxRank(legalCards, Suit.SPADES);
        } else {
          // don't lead spades when I have the Q, K, or A - instead lead the suite I have the least
          // of (to generate a void)
          final Optional<Suit> leastSuit =
              legalCards.stream()
                  .filter(c -> !c.getSuit().equals(Suit.SPADES))
                  .collect(Collectors.groupingBy(Card::getSuit, Collectors.counting()))
                  .entrySet()
                  .stream()
                  .min(Comparator.comparing(Map.Entry::getValue))
                  .map(Map.Entry::getKey);
          if (leastSuit.isPresent()) {
            return StrategyHelper.getMinRank(legalCards, leastSuit.get());
          } else {
            // I can only lead spades - lead the queen if ace or king will be played
            if (isSafeToPlayQueenOfSpades(round, player)) {
              return Optional.of(HeartsGame.QUEEN_OF_SPADES);
            }
            // lead the lowest spade that is not the queen
            final List<Card> allButQueen =
                legalCards.stream()
                    .filter(card -> !card.equals(HeartsGame.QUEEN_OF_SPADES))
                    .toList();
            return StrategyHelper.getMinRank(allButQueen, Suit.SPADES);
          }
        }
      }
    }
    // lead a card from the longest suit (not hearts)
    final Set<Suit> legalSuits = StrategyHelper.getSuits(legalCards);
    legalSuits.remove(Suit.HEARTS);
    if (!legalSuits.isEmpty()) {
      final List<Card> remainingLegalSuitCards =
          StrategyHelper.filterSuit(round.getRemainingCards(), legalSuits).toList();
      // rank remaining suits by number of cards remaining
      List<Suit> rankedSuits =
          remainingLegalSuitCards.stream()
              .collect(Collectors.groupingBy(Card::getSuit, Collectors.counting()))
              .entrySet()
              .stream()
              .sorted(Map.Entry.<Suit, Long>comparingByValue())
              .map(Map.Entry::getKey)
              .toList();
      rankedSuits = new ArrayList<>(rankedSuits);
      Collections.reverse(rankedSuits);

      for (Suit suit : rankedSuits) {
        Optional<Card> optionalCard = selectLeadCardOfSuit(round, player, suit, legalCards);
        if (optionalCard.isPresent()) {
          return optionalCard;
        }
      }
    }

    // TODO: this just returns a low card
    return legalCards.stream()
        .min(Comparator.comparing(Card::getRank))
        .or(() -> Optional.of(legalCards.iterator().next()));
  }

  /**
   * Select a high or low card of the provided suit to lead based on the number of remaining cards
   * in the suit.
   *
   * @param round
   * @param player
   * @param suit
   * @param legalCards
   * @return
   */
  protected Optional<Card> selectLeadCardOfSuit(
      final RoundState round, final Player player, final Suit suit, final Set<Card> legalCards) {
    long playedCount = StrategyHelper.filterSuit(round.getPlayedCards(), suit).count();
    long inMyHandCount = StrategyHelper.filterSuit(round.getHand(player), suit).count();
    long remainingCount = 13 - playedCount - inMyHandCount;
    if (remainingCount > 0) {
      if (remainingCount > 4 && !suit.equals(Suit.HEARTS)) {
        return StrategyHelper.getMaxRank(round.getHand(player), suit);
      } else {
        return StrategyHelper.getMinRank(round.getHand(player), suit);
      }
    } else {
      return Optional.empty();
    }
  }

  protected List<Card> selectCardsToPass(
      final RoundState round, final Player player, final Pass pass) {
    final List<Card> cardsToPass = new ArrayList<>(selectSpadesToPass(round, player));

    if (cardsToPass.size() < 3) {
      final List<Card> spades =
          StrategyHelper.filterSuit(round.getHand(player), Suit.SPADES).toList();
      final List<Card> others = new ArrayList<>(round.getHand(player));
      others.removeAll(spades);
      final List<Suit> suits = List.of(Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS);
      Rank rank = Rank.ACE;
      while (cardsToPass.size() < 3) {
        for (final Suit suit : suits) {
          if (others.contains(new Card(suit, rank)) && (cardsToPass.size() < 3)) {
            cardsToPass.add(new Card(suit, rank));
          }
        }
        rank = rank.getPrevious().get();
      }
    }
    return cardsToPass;
  }

  protected List<Card> selectSpadesToPass(final RoundState round, final Player player) {
    final List<Card> spades =
        StrategyHelper.filterSuit(round.getHand(player), Suit.SPADES).toList();
    // if spades are a long suit (4 or more), it's OK to keep them
    if (spades.size() < 4) {
      // otherwise, pass any spades queen and higher
      return StrategyHelper.getHigherCards(spades, Suit.SPADES, Rank.JACK);
    }
    return List.of();
  }
}
