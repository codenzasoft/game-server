package com.codenzasoft.games.hearts

import com.codenzasoft.games.common.cards.Card
import com.codenzasoft.games.common.cards.Rank
import com.codenzasoft.games.common.cards.Suit
import spock.lang.Specification

import static com.codenzasoft.games.hearts.HeartsStateBuilder.*

class HeartsScoreSpec extends Specification {

    // ── Convenience card constants ────────────────────────────────────────────

    static final CLUBS_2     = new Card(Suit.CLUBS,  Rank.TWO)
    static final CLUBS_3     = new Card(Suit.CLUBS,  Rank.THREE)
    static final CLUBS_4     = new Card(Suit.CLUBS,  Rank.FOUR)
    static final CLUBS_5     = new Card(Suit.CLUBS,  Rank.FIVE)
    static final CLUBS_6     = new Card(Suit.CLUBS,  Rank.SIX)
    static final CLUBS_7     = new Card(Suit.CLUBS,  Rank.SEVEN)
    static final HEARTS_2    = new Card(Suit.HEARTS, Rank.TWO)
    static final HEARTS_3    = new Card(Suit.HEARTS, Rank.THREE)
    static final HEARTS_4    = new Card(Suit.HEARTS, Rank.FOUR)
    static final QUEEN_SPADES = new Card(Suit.SPADES, Rank.QUEEN)

    // ── Helpers ───────────────────────────────────────────────────────────────

    // One complete trick where nobody plays a point card
    TrickState noPointTrick(int number = 1) {
        trick(number: number, playedCards: [
            played("alice", CLUBS_2),
            played("bob",   CLUBS_3),
            played("carol", CLUBS_4),
            played("dave",  CLUBS_5),
        ])
    }

    // ── Score map completeness ────────────────────────────────────────────────

    def "score map includes all players when no rounds have been played"() {
        def s = state()

        expect:
        s.score.keySet() == PLAYERS as Set
        s.score.values().every { it == 0 }
    }

    def "score map includes all players even when no point cards were played"() {
        def s = state(rounds: [round(tricks: [noPointTrick()])])

        expect:
        s.score.keySet() == PLAYERS as Set
        s.score.values().every { it == 0 }
    }

    // ── Per-card point values ─────────────────────────────────────────────────

    def "a heart card scores 1 point for the player who played it"() {
        def s = state(rounds: [round(tricks: [
            trick(playedCards: [
                played("alice", HEARTS_2),
                played("bob",   CLUBS_2),
                played("carol", CLUBS_3),
                played("dave",  CLUBS_4),
            ])
        ])])

        expect:
        s.score["alice"] == 1
        s.score["bob"]   == 0
        s.score["carol"] == 0
        s.score["dave"]  == 0
    }

    def "the queen of spades scores 13 points for the player who played it"() {
        def s = state(rounds: [round(tricks: [
            trick(playedCards: [
                played("alice", QUEEN_SPADES),
                played("bob",   CLUBS_2),
                played("carol", CLUBS_3),
                played("dave",  CLUBS_4),
            ])
        ])])

        expect:
        s.score["alice"] == 13
        s.score["bob"]   == 0
        s.score["carol"] == 0
        s.score["dave"]  == 0
    }

    // ── Cross-round accumulation ──────────────────────────────────────────────

    def "scores accumulate across rounds"() {
        def roundWithAliceHeart = { int n ->
            round(number: n, tricks: [
                trick(playedCards: [
                    played("alice", HEARTS_2),
                    played("bob",   CLUBS_2),
                    played("carol", CLUBS_3),
                    played("dave",  CLUBS_4),
                ])
            ])
        }
        def s = state(rounds: [roundWithAliceHeart(1), roundWithAliceHeart(2)])

        expect:
        s.score["alice"] == 2
        s.score["bob"]   == 0
    }

    // ── Shoot the moon ────────────────────────────────────────────────────────

    def "shooting the moon gives the shooter 0 points and each opponent 26"() {
        // alice plays all 13 hearts (13 tricks × 1 pt) + the queen of spades (13 pts) = 26 pts
        // note: 14 tricks is not a valid game hand but correctly exercises the scoring logic
        given:
        def heartTricks = Rank.values().collect { rank ->
            trick(playedCards: [
                played("alice", new Card(Suit.HEARTS, rank)),
                played("bob",   CLUBS_2),
                played("carol", CLUBS_3),
                played("dave",  CLUBS_4),
            ])
        }
        def qosTrick = trick(playedCards: [
            played("alice", QUEEN_SPADES),
            played("bob",   CLUBS_5),
            played("carol", CLUBS_6),
            played("dave",  CLUBS_7),
        ])
        def s = state(rounds: [round(tricks: heartTricks + [qosTrick])])

        expect:
        s.score["alice"] == 0
        s.score["bob"]   == 26
        s.score["carol"] == 26
        s.score["dave"]  == 26
    }

    // ── Winner ────────────────────────────────────────────────────────────────

    def "no winner when game is still in progress"() {
        def s = state(rounds: [round(tricks: [noPointTrick()])])

        expect:
        s.winner == null
    }

    def "no winner when the game was quit"() {
        def s = state(quit: true, rounds: [round(tricks: [noPointTrick()])])

        expect:
        s.winner == null
    }

    def "the player with the unique lowest score wins when the game is over"() {
        // alice=0, bob=1, carol=13, dave=1  →  alice has unique minimum
        def s = state(over: true, rounds: [round(tricks: [
            trick(playedCards: [
                played("alice", CLUBS_2),
                played("bob",   HEARTS_2),
                played("carol", QUEEN_SPADES),
                played("dave",  HEARTS_3),
            ])
        ])])

        expect:
        s.winner == "alice"
    }

    def "no winner when multiple players share the lowest score"() {
        // alice=0, bob=0, carol=1, dave=1  →  alice and bob tied
        def s = state(over: true, rounds: [round(tricks: [
            trick(playedCards: [
                played("alice", CLUBS_2),
                played("bob",   CLUBS_3),
                played("carol", HEARTS_2),
                played("dave",  HEARTS_3),
            ])
        ])])

        expect:
        s.winner == null
    }

    // ── Tied players ──────────────────────────────────────────────────────────

    def "tied lists all players sharing the lowest score"() {
        // alice=0, bob=0, carol=1, dave=1  →  tied: alice and bob
        def s = state(over: true, rounds: [round(tricks: [
            trick(playedCards: [
                played("alice", CLUBS_2),
                played("bob",   CLUBS_3),
                played("carol", HEARTS_2),
                played("dave",  HEARTS_3),
            ])
        ])])

        expect:
        s.tied as Set == ["alice", "bob"] as Set
    }

    def "tied is empty when there is a clear winner"() {
        def s = state(over: true, rounds: [round(tricks: [
            trick(playedCards: [
                played("alice", CLUBS_2),
                played("bob",   HEARTS_2),
                played("carol", QUEEN_SPADES),
                played("dave",  HEARTS_3),
            ])
        ])])

        expect:
        s.tied.isEmpty()
    }

    def "tied is empty when the game is not over"() {
        def s = state(rounds: [round(tricks: [noPointTrick()])])

        expect:
        s.tied.isEmpty()
    }
}
