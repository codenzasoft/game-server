package com.codenzasoft.games.hearts.strategies

import com.codenzasoft.games.common.Player
import com.codenzasoft.games.common.cards.Card
import com.codenzasoft.games.common.cards.Rank
import com.codenzasoft.games.common.cards.Suit
import com.codenzasoft.games.hearts.*
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Executors

class DefensiveStrategySpec extends Specification {

    static Card c(Suit suit, Rank rank) { new Card(suit, rank) }

    static final Card QS  = c(Suit.SPADES,   Rank.QUEEN)
    static final Card KS  = c(Suit.SPADES,   Rank.KING)
    static final Card AS  = c(Suit.SPADES,   Rank.ACE)
    static final Card JS  = c(Suit.SPADES,   Rank.JACK)
    static final Card _5S = c(Suit.SPADES,   Rank.FIVE)
    static final Card _3S = c(Suit.SPADES,   Rank.THREE)
    static final Card _2S = c(Suit.SPADES,   Rank.TWO)
    static final Card AC  = c(Suit.CLUBS,    Rank.ACE)
    static final Card KC  = c(Suit.CLUBS,    Rank.KING)
    static final Card _7C = c(Suit.CLUBS,    Rank.SEVEN)
    static final Card _2C = c(Suit.CLUBS,    Rank.TWO)
    static final Card _3C = c(Suit.CLUBS,    Rank.THREE)
    static final Card AD  = c(Suit.DIAMONDS, Rank.ACE)
    static final Card KD  = c(Suit.DIAMONDS, Rank.KING)
    static final Card JD  = c(Suit.DIAMONDS, Rank.JACK)
    static final Card _2D = c(Suit.DIAMONDS, Rank.TWO)
    static final Card _3D = c(Suit.DIAMONDS, Rank.THREE)
    static final Card _4D = c(Suit.DIAMONDS, Rank.FOUR)
    static final Card _5D = c(Suit.DIAMONDS, Rank.FIVE)
    static final Card _6D = c(Suit.DIAMONDS, Rank.SIX)
    static final Card _7D = c(Suit.DIAMONDS, Rank.SEVEN)
    static final Card AH  = c(Suit.HEARTS,   Rank.ACE)
    static final Card KH  = c(Suit.HEARTS,   Rank.KING)

    def alice = new Player(UUID.randomUUID(), "alice")
    def bob   = new Player(UUID.randomUUID(), "bob")
    def carol = new Player(UUID.randomUUID(), "carol")
    def dave  = new Player(UUID.randomUUID(), "dave")

    // ── followTrump: trick-number check ──────────────────────────────────────────

    def "followTrump plays highest card on trick 1 of any round"() {
        given: "trick 1, bob led 7♣ — alice follows clubs"
        def strategy = new DefensiveStrategy()
        def game = Mock(HeartsGame)
        def trick = new TrickState(1, "bob", [new PlayedCard("bob", _7C)])
        def round = roundWithTrick("alice", [AC, _2C], trick)
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [AC, _2C] as Set)

        then: "plays A♣ — the highest club, since trick 1 is still safe"
        1 * game.playCard(alice, AC)
    }

    def "followTrump plays below the current winner on trick 2+"() {
        given: "trick 2, bob led 7♣ — alice follows clubs and can duck"
        def strategy = new DefensiveStrategy()
        def game = Mock(HeartsGame)
        def trick = new TrickState(2, "bob", [new PlayedCard("bob", _7C)])
        def round = roundWithTrick("alice", [AC, _2C], trick)
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [AC, _2C] as Set)

        then: "plays 2♣ — the highest card that ducks under 7♣"
        1 * game.playCard(alice, _2C)
    }

    // ── followSpades: duck when points are in the trick ──────────────────────────

    def "followSpades ducks under the current trick winner when hearts have been sluffed (last player)"() {
        given: "bob leads 6♠; carol sluffs 2♥ (void in spades); dave plays 9♠ (winning); alice is last"
        def strategy = new DefensiveStrategy()
        def game = Mock(HeartsGame)
        def trick = new TrickState(2, "bob", [
            new PlayedCard("bob",   c(Suit.SPADES, Rank.SIX)),
            new PlayedCard("carol", c(Suit.HEARTS, Rank.TWO)),
            new PlayedCard("dave",  c(Suit.SPADES, Rank.NINE)),
        ])
        // alice has J♠ (beats 9♠) and 3♠ (ducks under 9♠)
        def round = roundWithTrick("alice", [JS, _3S], trick)
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [JS, _3S] as Set)

        then: "plays 3♠ to duck under 9♠ — avoids taking the sluffed heart"
        1 * game.playCard(alice, _3S)
    }

    def "followSpades ducks under the actual trick winner mid-trick (not just below Q♠)"() {
        given: "bob leads 8♠; carol sluffs 2♥; alice plays before dave — must pick a card"
        def strategy = new DefensiveStrategy()
        def game = Mock(HeartsGame)
        def trick = new TrickState(2, "bob", [
            new PlayedCard("bob",   c(Suit.SPADES, Rank.EIGHT)),
            new PlayedCard("carol", c(Suit.HEARTS, Rank.TWO)),
        ])
        // alice has J♠ (highest below Q♠ but beats 8♠) and 3♠ (ducks under 8♠)
        def round = roundWithTrick("alice", [JS, _3S], trick)
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [JS, _3S] as Set)

        then: "plays 3♠ — ducks under 8♠ rather than J♠ (highest below Q♠) which would win the heart"
        1 * game.playCard(alice, _3S)
    }

    // ── selectLeadCardOfSuit: rank-check guard ────────────────────────────────────

    def "leads LOW when holding the highest remaining card in a suit despite many opponent cards"() {
        given: "A♦ K♦ Q♦ 10♦ 9♦ 8♦ already played; alice holds J♦ 3♦; opponents hold 7♦ 6♦ 5♦ 4♦ 2♦"
        // remainingCount = 13 - 6 played - 2 alice = 5 (> 4 threshold)
        // but no opponent card outranks J♦ — old code led J♦ (winning the trick); new code leads 3♦
        def strategy = new DefensiveStrategy()
        def game = Mock(HeartsGame)
        def alreadyPlayed = [AD, KD, c(Suit.DIAMONDS, Rank.QUEEN),
                             c(Suit.DIAMONDS, Rank.TEN), c(Suit.DIAMONDS, Rank.NINE),
                             c(Suit.DIAMONDS, Rank.EIGHT)]
        def hands = ["alice": [JD, _3D], "bob": [_7D, _6D, _5D, _4D, _2D]]
        def round = roundLeading("alice", hands, alreadyPlayed)
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [JD, _3D] as Set)

        then: "leads 3♦ (low) — J♦ would win the trick since no opponent holds a higher diamond"
        1 * game.playCard(alice, _3D)
    }

    def "leads HIGH when an opponent holds a card that outranks the lead candidate"() {
        given: "K♦ Q♦ 10♦ 9♦ 8♦ already played; alice holds J♦ 3♦; opponents hold A♦ 7♦ 6♦ 5♦ 4♦ 2♦"
        // remainingCount = 13 - 5 played - 2 alice = 6 (> 4), and A♦ > J♦ → lead J♦
        def strategy = new DefensiveStrategy()
        def game = Mock(HeartsGame)
        def alreadyPlayed = [KD, c(Suit.DIAMONDS, Rank.QUEEN),
                             c(Suit.DIAMONDS, Rank.TEN), c(Suit.DIAMONDS, Rank.NINE),
                             c(Suit.DIAMONDS, Rank.EIGHT)]
        def hands = ["alice": [JD, _3D], "bob": [AD, _7D, _6D, _5D, _4D, _2D]]
        def round = roundLeading("alice", hands, alreadyPlayed)
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [JD, _3D] as Set)

        then: "leads J♦ (high) — A♦ held by opponent guarantees alice loses the trick"
        1 * game.playCard(alice, JD)
    }

    def "leads LOW when only a few opponent cards remain in the suit (≤4 threshold)"() {
        given: "9 diamonds already played; alice holds J♦ 3♦; opponents hold only 4♦ 2♦"
        // remainingCount = 13 - 9 played - 2 alice = 2 (≤ 4) → always lead low
        def strategy = new DefensiveStrategy()
        def game = Mock(HeartsGame)
        def alreadyPlayed = [AD, KD, c(Suit.DIAMONDS, Rank.QUEEN), c(Suit.DIAMONDS, Rank.TEN),
                             c(Suit.DIAMONDS, Rank.NINE), c(Suit.DIAMONDS, Rank.EIGHT),
                             _7D, _6D, _5D]
        def hands = ["alice": [JD, _3D], "bob": [_4D, _2D]]
        def round = roundLeading("alice", hands, alreadyPlayed)
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [JD, _3D] as Set)

        then: "leads 3♦ (low) — few cards remain, low lead is the safe choice"
        1 * game.playCard(alice, _3D)
    }

    // ── selectSpadesToPass: low-card protection threshold ────────────────────────

    def "spades are passed when 4+ spades but fewer than 2 low cards"() {
        given: "Q♠ K♠ A♠ 2♠ — only one low spade, not enough protection"
        def strategy = new DefensiveStrategy()
        def hand = [QS, KS, AS, _2S, AH, KH, _2C, _3C, _2D, _3D,
                    c(Suit.CLUBS, Rank.FOUR), c(Suit.CLUBS, Rank.FIVE), c(Suit.CLUBS, Rank.SIX)]
        def round = roundWithHand("alice", hand)

        when:
        def passed = strategy.selectSpadesToPass(round, alice)

        then: "Q♠ K♠ A♠ are passed despite holding 4 spades"
        passed.containsAll([QS, KS, AS])
    }

    def "spades are kept when 4+ spades and at least 2 low cards"() {
        given: "Q♠ K♠ A♠ J♠ 5♠ — two low spades provide adequate protection"
        def strategy = new DefensiveStrategy()
        def hand = [QS, KS, AS, JS, _5S, AH, KH, _2C, _3C, _2D, _3D,
                    c(Suit.CLUBS, Rank.FOUR), c(Suit.CLUBS, Rank.FIVE)]
        def round = roundWithHand("alice", hand)

        when:
        def passed = strategy.selectSpadesToPass(round, alice)

        then: "no spades are passed — the long suit is well-protected"
        passed.isEmpty()
    }

    // ── selectCardsToPass: void creation ─────────────────────────────────────────

    def "pass creates a void in a singleton non-hearts/non-spades suit"() {
        given: "alice holds 1 club — passing it creates a void to dump dangerous cards later"
        def strategy = new DefensiveStrategy()
        def hand = [AH, KH, _2C, _2D, _3D,
                    c(Suit.HEARTS,   Rank.QUEEN),
                    c(Suit.HEARTS,   Rank.JACK),
                    c(Suit.HEARTS,   Rank.TEN),
                    c(Suit.DIAMONDS, Rank.FOUR),
                    c(Suit.DIAMONDS, Rank.FIVE),
                    c(Suit.DIAMONDS, Rank.SIX),
                    c(Suit.DIAMONDS, Rank.SEVEN),
                    c(Suit.DIAMONDS, Rank.EIGHT)]
        def round = roundWithHand("alice", hand)

        when:
        def passed = strategy.selectCardsToPass(round, alice, Pass.LEFT)

        then: "2♣ is included in the pass to void clubs"
        passed.contains(_2C)
        passed.size() == 3
    }

    def "pass creates a void in a doubleton suit when budget allows"() {
        given: "alice has no spades to pass and holds exactly 2 clubs"
        def strategy = new DefensiveStrategy()
        def hand = [AH, KH, _2C, _3C, _2D, _3D,
                    c(Suit.HEARTS,   Rank.QUEEN),
                    c(Suit.HEARTS,   Rank.JACK),
                    c(Suit.HEARTS,   Rank.TEN),
                    c(Suit.DIAMONDS, Rank.FOUR),
                    c(Suit.DIAMONDS, Rank.FIVE),
                    c(Suit.DIAMONDS, Rank.SIX),
                    c(Suit.DIAMONDS, Rank.SEVEN)]
        def round = roundWithHand("alice", hand)

        when:
        def passed = strategy.selectCardsToPass(round, alice, Pass.LEFT)

        then: "both clubs are passed to void the suit"
        passed.containsAll([_2C, _3C])
        passed.size() == 3
    }

    def "no partial void attempted when suit has more cards than budget"() {
        given: "alice has 3 clubs — voiding would cost the full 3-card budget; prefer high-card passes"
        def strategy = new DefensiveStrategy()
        def hand = [AH, KH, AC, _2C, _3C, _2D, _3D,
                    c(Suit.HEARTS,   Rank.QUEEN),
                    c(Suit.HEARTS,   Rank.JACK),
                    c(Suit.HEARTS,   Rank.TEN),
                    c(Suit.DIAMONDS, Rank.FOUR),
                    c(Suit.DIAMONDS, Rank.FIVE),
                    c(Suit.DIAMONDS, Rank.SIX)]
        def round = roundWithHand("alice", hand)

        when:
        def passed = strategy.selectCardsToPass(round, alice, Pass.LEFT)

        then: "exactly 3 cards passed, no partial void forced"
        passed.size() == 3
    }

    // ── full game integration ─────────────────────────────────────────────────────

    def "four DefensiveStrategy bots complete a full game without errors"() {
        def conditions = new PollingConditions(timeout: 15, initialDelay: 0.5, delay: 0.5)
        def game = new HeartsGame.Builder()
            .withPlayers([alice, bob, carol, dave])
            .withId(UUID.randomUUID())
            .withNotificationExecutor(Executors.newSingleThreadExecutor())
            .addPlayerObserver(alice, new DefensiveStrategy())
            .addPlayerObserver(bob,   new DefensiveStrategy())
            .addPlayerObserver(carol, new DefensiveStrategy())
            .addPlayerObserver(dave,  new DefensiveStrategy())
            .build()

        expect:
        conditions.eventually {
            game.getGameState().isOver()
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static RoundState roundWithHand(String playerName, List<Card> hand) {
        def leadingTrick = new TrickState(1, playerName, [])
        new RoundState(
            1, Pass.LEFT, playerName,
            [(playerName): hand], [:], true,
            [leadingTrick],
            [(playerName): hand]
        )
    }

    private static RoundState roundWithTrick(String playerName, List<Card> hand, TrickState trick) {
        new RoundState(
            1, Pass.KEEPER, playerName,
            [(playerName): hand], [:], true,
            [trick],
            [(playerName): hand]
        )
    }

    /**
     * Builds a round where {@code playerName} is about to lead, with specific per-player hands
     * and a list of cards already played in previous tricks (lumped into one history trick entry).
     */
    private static RoundState roundLeading(
            String playerName, Map<String, List<Card>> hands, List<Card> alreadyPlayed) {
        def currentTrick = new TrickState(alreadyPlayed.isEmpty() ? 1 : 2, playerName, [])
        def tricks = alreadyPlayed.isEmpty()
            ? [currentTrick]
            : [new TrickState(1, "bob", alreadyPlayed.collect { new PlayedCard("bob", it) }),
               currentTrick]
        new RoundState(1, Pass.KEEPER, playerName, hands, [:], true, tricks, hands)
    }
}
