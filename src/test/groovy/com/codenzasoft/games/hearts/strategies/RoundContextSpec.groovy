package com.codenzasoft.games.hearts.strategies

import com.codenzasoft.games.common.cards.Card
import com.codenzasoft.games.common.cards.Rank
import com.codenzasoft.games.common.cards.Suit
import com.codenzasoft.games.hearts.HeartsStateBuilder
import com.codenzasoft.games.hearts.PlayedCard
import com.codenzasoft.games.hearts.TrickState
import spock.lang.Specification

class RoundContextSpec extends Specification {

    // ── helpers ──────────────────────────────────────────────────────────────

    static Card c(Suit suit, Rank rank) { new Card(suit, rank) }

    /**
     * Builds a complete 4-card trick. The first card determines trump (lead suit).
     * Winner is the highest card of that suit in the trick.
     */
    static TrickState completeTrick(List<PlayedCard> played) {
        assert played.size() == 4
        new TrickState(1, played[0].player(), played)
    }

    /** Trick where alice leads Q♠ and wins (13 pts). Other players play off-suit. */
    static TrickState aliceWinsQueenOfSpades() {
        completeTrick([
            new PlayedCard("alice", c(Suit.SPADES, Rank.QUEEN)),
            new PlayedCard("bob",   c(Suit.CLUBS,  Rank.TWO)),
            new PlayedCard("carol", c(Suit.CLUBS,  Rank.THREE)),
            new PlayedCard("dave",  c(Suit.DIAMONDS, Rank.FOUR)),
        ])
    }

    /** Trick where alice leads A♥ and wins (hearts played = 1+ pts, all going to alice). */
    static TrickState aliceWinsHearts(int heartCount) {
        assert heartCount >= 1 && heartCount <= 3
        def extra = (1..heartCount).collect { i ->
            def player = ["bob", "carol", "dave"][i - 1]
            new PlayedCard(player, c(Suit.HEARTS, [Rank.TWO, Rank.THREE, Rank.FOUR][i - 1]))
        }
        completeTrick([
            new PlayedCard("alice", c(Suit.HEARTS, Rank.ACE)),
            *extra,
            *(heartCount < 3 ? [new PlayedCard(["bob", "carol", "dave"][heartCount], c(Suit.CLUBS, Rank.FIVE))] : []),
        ])
    }

    /** Trick where bob leads A♥ and wins (1+ pts going to bob, not alice). */
    static TrickState bobWinsHeart() {
        completeTrick([
            new PlayedCard("bob",   c(Suit.HEARTS, Rank.ACE)),
            new PlayedCard("carol", c(Suit.HEARTS, Rank.TWO)),
            new PlayedCard("dave",  c(Suit.CLUBS,  Rank.THREE)),
            new PlayedCard("alice", c(Suit.CLUBS,  Rank.FOUR)),
        ])
    }

    // ── shootingPlayer ────────────────────────────────────────────────────────

    def "returns empty when no rounds have been played"() {
        expect:
        !RoundContext.shootingPlayer(HeartsStateBuilder.state()).isPresent()
    }

    def "returns empty when current round is complete"() {
        given: "HeartsStateBuilder.round() has empty hands → isComplete = true"
        def state = HeartsStateBuilder.state(rounds: [HeartsStateBuilder.round()])

        expect:
        !RoundContext.shootingPlayer(state).isPresent()
    }

    def "returns empty when no player meets the threshold"() {
        given: "alice has Q♠ alone (13 pts) — just below threshold of 14"
        def trick = aliceWinsQueenOfSpades()
        def state = HeartsStateBuilder.state(
            rounds: [HeartsStateBuilder.inProgressRound(tricks: [trick])]
        )

        expect:
        trick.score == ["alice": 13]
        !RoundContext.shootingPlayer(state).isPresent()
    }

    def "detects shooter when a player has Q♠ plus a heart"() {
        given: "alice wins Q♠ (13) then A♥+2♥ trick (2 pts) = 15 pts total"
        def trick1 = aliceWinsQueenOfSpades()
        def trick2 = completeTrick([
            new PlayedCard("alice", c(Suit.HEARTS, Rank.ACE)),
            new PlayedCard("bob",   c(Suit.HEARTS, Rank.TWO)),
            new PlayedCard("carol", c(Suit.CLUBS,  Rank.THREE)),
            new PlayedCard("dave",  c(Suit.CLUBS,  Rank.FOUR)),
        ])
        def state = HeartsStateBuilder.state(
            rounds: [HeartsStateBuilder.inProgressRound(tricks: [trick1, trick2])]
        )

        expect:
        RoundContext.shootingPlayer(state).orElse(null) == "alice"
    }

    def "returns empty when a player reaches threshold but the round is complete"() {
        given: "round is complete (empty hands), score doesn't matter"
        def trick = aliceWinsQueenOfSpades()
        def state = HeartsStateBuilder.state(
            rounds: [HeartsStateBuilder.round(tricks: [trick])]
        )

        expect:
        !RoundContext.shootingPlayer(state).isPresent()
    }

    // ── hasMoonShotFailed ────────────────────────────────────────────────────

    def "returns false when no rounds have been played"() {
        expect:
        !RoundContext.hasMoonShotFailed(HeartsStateBuilder.state(), "alice")
    }

    def "returns false when alice wins all point tricks"() {
        given:
        def trick1 = aliceWinsQueenOfSpades()
        def trick2 = completeTrick([
            new PlayedCard("alice", c(Suit.HEARTS, Rank.ACE)),
            new PlayedCard("bob",   c(Suit.CLUBS,  Rank.TWO)),
            new PlayedCard("carol", c(Suit.CLUBS,  Rank.THREE)),
            new PlayedCard("dave",  c(Suit.CLUBS,  Rank.FOUR)),
        ])
        def state = HeartsStateBuilder.state(
            rounds: [HeartsStateBuilder.inProgressRound(tricks: [trick1, trick2])]
        )

        expect:
        !RoundContext.hasMoonShotFailed(state, "alice")
    }

    def "returns false when all completed tricks have no points"() {
        given:
        def pointlessTrick = completeTrick([
            new PlayedCard("alice", c(Suit.CLUBS, Rank.ACE)),
            new PlayedCard("bob",   c(Suit.CLUBS, Rank.TWO)),
            new PlayedCard("carol", c(Suit.CLUBS, Rank.THREE)),
            new PlayedCard("dave",  c(Suit.CLUBS, Rank.FOUR)),
        ])
        def state = HeartsStateBuilder.state(
            rounds: [HeartsStateBuilder.inProgressRound(tricks: [pointlessTrick])]
        )

        expect:
        !RoundContext.hasMoonShotFailed(state, "alice")
    }

    def "returns true when bob wins a heart trick"() {
        given:
        def trick = bobWinsHeart()
        def state = HeartsStateBuilder.state(
            rounds: [HeartsStateBuilder.inProgressRound(tricks: [trick])]
        )

        expect:
        RoundContext.hasMoonShotFailed(state, "alice")
    }

    // ── resolveMode ───────────────────────────────────────────────────────────

    def "resolves DEFENSIVE when no threat detected"() {
        expect:
        RoundContext.resolveMode(HeartsStateBuilder.state(), "alice") == Mode.DEFENSIVE
    }

    def "resolves OFFENSIVE for the player who is shooting"() {
        given:
        def trick1 = aliceWinsQueenOfSpades()
        def trick2 = completeTrick([
            new PlayedCard("alice", c(Suit.HEARTS, Rank.ACE)),
            new PlayedCard("bob",   c(Suit.HEARTS, Rank.TWO)),
            new PlayedCard("carol", c(Suit.CLUBS,  Rank.THREE)),
            new PlayedCard("dave",  c(Suit.CLUBS,  Rank.FOUR)),
        ])
        def state = HeartsStateBuilder.state(
            rounds: [HeartsStateBuilder.inProgressRound(tricks: [trick1, trick2])]
        )

        expect:
        RoundContext.resolveMode(state, "alice") == Mode.OFFENSIVE
    }

    def "resolves SABOTEUR for players who are not the shooter"() {
        given:
        def trick1 = aliceWinsQueenOfSpades()
        def trick2 = completeTrick([
            new PlayedCard("alice", c(Suit.HEARTS, Rank.ACE)),
            new PlayedCard("bob",   c(Suit.HEARTS, Rank.TWO)),
            new PlayedCard("carol", c(Suit.CLUBS,  Rank.THREE)),
            new PlayedCard("dave",  c(Suit.CLUBS,  Rank.FOUR)),
        ])
        def state = HeartsStateBuilder.state(
            rounds: [HeartsStateBuilder.inProgressRound(tricks: [trick1, trick2])]
        )

        expect:
        RoundContext.resolveMode(state, "bob")   == Mode.SABOTEUR
        RoundContext.resolveMode(state, "carol") == Mode.SABOTEUR
        RoundContext.resolveMode(state, "dave")  == Mode.SABOTEUR
    }
}
