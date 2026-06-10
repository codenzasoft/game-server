package com.codenzasoft.games.hearts.strategies

import com.codenzasoft.games.common.cards.Card
import com.codenzasoft.games.common.cards.Rank
import com.codenzasoft.games.common.cards.Suit
import spock.lang.Specification

class HandEvaluatorSpec extends Specification {

    // ── helpers ──────────────────────────────────────────────────────────────

    static Card c(Suit suit, Rank rank) { new Card(suit, rank) }

    static final Card AH = c(Suit.HEARTS, Rank.ACE)
    static final Card KH = c(Suit.HEARTS, Rank.KING)
    static final Card QH = c(Suit.HEARTS, Rank.QUEEN)
    static final Card JH = c(Suit.HEARTS, Rank.JACK)
    static final Card TH = c(Suit.HEARTS, Rank.TEN)
    static final Card NH = c(Suit.HEARTS, Rank.NINE)
    static final Card QS = c(Suit.SPADES, Rank.QUEEN)
    static final Card AS = c(Suit.SPADES, Rank.ACE)
    static final Card KS = c(Suit.SPADES, Rank.KING)
    static final Card AC = c(Suit.CLUBS, Rank.ACE)
    static final Card AD = c(Suit.DIAMONDS, Rank.ACE)

    // ── scoring ───────────────────────────────────────────────────────────────

    def "empty hand scores zero"() {
        expect:
        HandEvaluator.shootingScore([]) == 0
    }

    def "heart ranks score correctly"() {
        expect:
        HandEvaluator.shootingScore([AH]) == 3
        HandEvaluator.shootingScore([KH]) == 3
        HandEvaluator.shootingScore([QH]) == 2
        HandEvaluator.shootingScore([JH]) == 2
        HandEvaluator.shootingScore([TH]) == 1
        HandEvaluator.shootingScore([NH]) == 1
        HandEvaluator.shootingScore([c(Suit.HEARTS, Rank.EIGHT)]) == 0
        HandEvaluator.shootingScore([c(Suit.HEARTS, Rank.TWO)]) == 0
    }

    def "Q♠ scores 2"() {
        expect:
        HandEvaluator.shootingScore([QS]) == 2
    }

    def "A♠ and K♠ each score 1"() {
        expect:
        HandEvaluator.shootingScore([AS]) == 1
        HandEvaluator.shootingScore([KS]) == 1
    }

    def "aces in clubs and diamonds each score 1"() {
        expect:
        HandEvaluator.shootingScore([AC]) == 1
        HandEvaluator.shootingScore([AD]) == 1
    }

    def "low non-heart cards score 0"() {
        expect:
        HandEvaluator.shootingScore([c(Suit.CLUBS, Rank.TWO)]) == 0
        HandEvaluator.shootingScore([c(Suit.DIAMONDS, Rank.SEVEN)]) == 0
        HandEvaluator.shootingScore([c(Suit.SPADES, Rank.JACK)]) == 0
    }

    def "strong shooting hand scores above threshold"() {
        given: "A♥ K♥ Q♥ J♥ 10♥ Q♠ A♣ — classic moon-shot stack"
        def hand = [AH, KH, QH, JH, TH, QS, AC]
        // 3 + 3 + 2 + 2 + 1 + 2 + 1 = 14

        expect:
        HandEvaluator.shootingScore(hand) == 14
        HandEvaluator.isShootViable(hand)
    }

    // ── viability threshold ───────────────────────────────────────────────────

    def "A♥ and K♥ alone do not reach the threshold"() {
        given: "score = 6, threshold = 10"
        def hand = [AH, KH]

        expect:
        !HandEvaluator.isShootViable(hand)
    }

    def "hand just at threshold is viable"() {
        given: "A♥ K♥ Q♥ J♥ — score = 3+3+2+2 = 10, exactly at threshold"
        def hand = [AH, KH, QH, JH]

        expect:
        HandEvaluator.shootingScore(hand) == 10
        HandEvaluator.isShootViable(hand)
    }

    def "weak all-low hand is not viable"() {
        given:
        def hand = [
            c(Suit.CLUBS, Rank.TWO), c(Suit.CLUBS, Rank.THREE),
            c(Suit.DIAMONDS, Rank.FOUR), c(Suit.SPADES, Rank.FIVE),
            c(Suit.HEARTS, Rank.TWO), c(Suit.HEARTS, Rank.THREE),
        ]

        expect:
        !HandEvaluator.isShootViable(hand)
    }
}
