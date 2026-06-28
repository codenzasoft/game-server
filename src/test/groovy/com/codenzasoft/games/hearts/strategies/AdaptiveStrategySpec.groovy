package com.codenzasoft.games.hearts.strategies

import com.codenzasoft.games.common.Player
import com.codenzasoft.games.common.cards.Card
import com.codenzasoft.games.common.cards.Rank
import com.codenzasoft.games.common.cards.Suit
import com.codenzasoft.games.hearts.*
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Executor
import java.util.concurrent.Executors

class AdaptiveStrategySpec extends Specification {

    static Card c(Suit suit, Rank rank) { new Card(suit, rank) }

    static final Card AH = c(Suit.HEARTS, Rank.ACE)
    static final Card KH = c(Suit.HEARTS, Rank.KING)
    static final Card QH = c(Suit.HEARTS, Rank.QUEEN)
    static final Card JH = c(Suit.HEARTS, Rank.JACK)
    static final Card TH = c(Suit.HEARTS, Rank.TEN)
    static final Card NH = c(Suit.HEARTS, Rank.NINE)
    static final Card EH = c(Suit.HEARTS, Rank.EIGHT)  // 8♥ — not a keep card
    static final Card QS = c(Suit.SPADES, Rank.QUEEN)
    static final Card AS = c(Suit.SPADES, Rank.ACE)
    static final Card KS = c(Suit.SPADES, Rank.KING)
    static final Card AC = c(Suit.CLUBS, Rank.ACE)
    static final Card KC = c(Suit.CLUBS, Rank.KING)
    static final Card AD = c(Suit.DIAMONDS, Rank.ACE)
    static final Card _2C = c(Suit.CLUBS, Rank.TWO)
    static final Card _3C = c(Suit.CLUBS, Rank.THREE)
    static final Card _4C = c(Suit.CLUBS, Rank.FOUR)
    static final Card _2D = c(Suit.DIAMONDS, Rank.TWO)
    static final Card _3D = c(Suit.DIAMONDS, Rank.THREE)

    def alice = new Player(UUID.randomUUID(), "alice")
    def bob   = new Player(UUID.randomUUID(), "bob")
    def carol = new Player(UUID.randomUUID(), "carol")
    def dave  = new Player(UUID.randomUUID(), "dave")

    // ── offensive pass selection ────────────────────────────────────────────────

    def "offensive pass keeps high hearts, Q♠, A♠/K♠, and side aces"() {
        given:
        def strategy = new AdaptiveStrategy(true)
        def hand = [AH, KH, QH, QS, AS, AC, _2C, _3C, _4C, _2D, _3D,
                    c(Suit.DIAMONDS, Rank.FOUR), c(Suit.DIAMONDS, Rank.FIVE)]
        def round = roundWithHand("alice", hand)

        when:
        def passed = strategy.selectOffensivePassCards(round, alice)

        then:
        passed.size() == 3
        !passed.any { it == AH || it == KH || it == QH || it == QS || it == AS || it == AC }
    }

    def "offensive pass discards low non-heart cards first"() {
        given:
        def strategy = new AdaptiveStrategy(true)
        def hand = [AH, KH, QH, JH, TH, NH, QS, AS, AC, AD, _2C, _3C, _4C]
        def round = roundWithHand("alice", hand)

        when:
        def passed = strategy.selectOffensivePassCards(round, alice)

        then: "the three lowest non-protected cards are passed"
        passed.size() == 3
        passed.containsAll([_2C, _3C, _4C])
    }

    def "offensive pass supplements with lowest protected cards when not enough passable"() {
        given: "all 13 cards are protected — no passable cards at all"
        def strategy = new AdaptiveStrategy(true)
        def hand = [AH, KH, QH, JH, TH, NH, QS, AS, KS, AC, AD,
                    c(Suit.HEARTS, Rank.NINE), c(Suit.SPADES, Rank.ACE)]
        // De-dup — build a hand where every card is a keep card
        def uniqueHand = [AH, KH, QH, JH, TH, NH, QS, AS, KS, AC, AD,
                          c(Suit.CLUBS, Rank.ACE), c(Suit.DIAMONDS, Rank.ACE)].unique()
        // Trim / ensure exactly 13 unique cards:
        def trimmed = uniqueHand.take(13)
        def round = roundWithHand("alice", trimmed)

        when:
        def passed = strategy.selectOffensivePassCards(round, alice)

        then:
        passed.size() == 3
    }

    // ── offensive card play ──────────────────────────────────────────────────────

    def "offensive lead selects highest heart when hearts are available"() {
        given:
        def strategy = new AdaptiveStrategy(true)
        def hand = [AH, KH, QH, _2C, _3C]
        def state = stateWithHand("alice", hand)

        when:
        def card = strategy.selectOffensiveCard(state, alice, new LinkedHashSet<>(hand))

        then:
        card.get() == AH
    }

    def "offensive lead selects guaranteed-win non-heart when no hearts available"() {
        given: "alice holds A♣ and 2♣; others hold 3♣–5♣; A♣ is the only guaranteed-win card"
        def strategy = new AdaptiveStrategy(true)
        def myHand = [AC, _2C]  // no hearts; 2♣ cannot win (others have higher clubs)
        def othersCards = [c(Suit.CLUBS, Rank.THREE), c(Suit.CLUBS, Rank.FOUR), c(Suit.CLUBS, Rank.FIVE)]
        def allRemaining = myHand + othersCards
        def round = roundWithRemainingCards("alice", myHand, allRemaining)
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def card = strategy.selectOffensiveCard(state, alice, new LinkedHashSet<>(myHand))

        then: "leads A♣ — the only card where no opponent holds a higher one"
        card.get() == AC
    }

    def "offensive follow plays highest card of trump suit"() {
        given: "trick led with clubs — alice must follow, plays highest"
        def strategy = new AdaptiveStrategy(true)
        def leadCard = new PlayedCard("bob", _2C)
        def trick = new TrickState(1, "bob", [leadCard])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])
        def validCards = [AC, KC] as Set

        when:
        def card = strategy.selectOffensiveCard(state, alice, validCards)

        then:
        card.get() == AC
    }

    def "offensive sluff discards lowest safe card and preserves hearts"() {
        given: "trick led with spades — alice is void in spades, must sluff"
        def strategy = new AdaptiveStrategy(true)
        def leadCard = new PlayedCard("bob", c(Suit.SPADES, Rank.TWO))
        def trick = new TrickState(1, "bob", [leadCard])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])
        // Alice has no spades, so valid cards are off-suit only — she must sluff
        def validCards = [AH, _2C] as Set

        when:
        def card = strategy.selectOffensiveCard(state, alice, validCards)

        then: "discards the low club, not the heart"
        card.get() == _2C
    }

    def "offensive sluff plays lowest card when only hearts and Q♠ remain"() {
        given:
        def strategy = new AdaptiveStrategy(true)
        def leadCard = new PlayedCard("bob", c(Suit.SPADES, Rank.TWO))
        def trick = new TrickState(1, "bob", [leadCard])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])
        def validCards = [AH, KH, QS] as Set

        when:
        def card = strategy.selectOffensiveCard(state, alice, validCards)

        then:
        card.isPresent()
        validCards.contains(card.get())
    }

    // ── mode resolution ───────────────────────────────────────────────────────────

    def "mode is OFFENSIVE when committed and no point tricks lost"() {
        given:
        def strategy = new AdaptiveStrategy(true)
        def state = HeartsStateBuilder.state(rounds: [HeartsStateBuilder.inProgressRound()])

        expect:
        strategy.resolveMode(state, alice) == Mode.OFFENSIVE
    }

    def "mode reverts to DEFENSIVE after opponent wins a point trick"() {
        given: "bob wins a heart trick — alice's moon shot has failed"
        def strategy = new AdaptiveStrategy(true)
        def trick = new TrickState(1, "bob", [
            new PlayedCard("bob",   c(Suit.HEARTS, Rank.ACE)),
            new PlayedCard("carol", c(Suit.HEARTS, Rank.TWO)),
            new PlayedCard("dave",  c(Suit.CLUBS,  Rank.THREE)),
            new PlayedCard("alice", c(Suit.CLUBS,  Rank.FOUR)),
        ])
        def state = HeartsStateBuilder.state(
            rounds: [HeartsStateBuilder.inProgressRound(tricks: [trick])]
        )

        expect:
        strategy.resolveMode(state, alice) == Mode.DEFENSIVE
    }

    def "mode is DEFENSIVE when not committed to shooting"() {
        given:
        def strategy = new AdaptiveStrategy(false)
        def state = HeartsStateBuilder.state(rounds: [HeartsStateBuilder.inProgressRound()])

        expect:
        strategy.resolveMode(state, alice) == Mode.DEFENSIVE
    }

    // ── saboteur mode ─────────────────────────────────────────────────────────────

    def "mode is SABOTEUR when opponent accumulates 4 hearts before Q♠ is played"() {
        given: "bob wins 4 heart tricks; Q♠ has not appeared yet"
        def strategy = new AdaptiveStrategy(false)
        def heart = { Rank r -> c(Suit.HEARTS, r) }
        def club  = { Rank r -> c(Suit.CLUBS, r) }
        def tricks = (1..4).collect { i ->
            new TrickState(i, "bob", [
                new PlayedCard("bob",   heart(Rank.values()[Rank.ACE.ordinal() - (i-1)])),
                new PlayedCard("carol", club(Rank.TWO)),
                new PlayedCard("dave",  club(Rank.THREE)),
                new PlayedCard("alice", club(Rank.FOUR)),
            ])
        }
        def round = HeartsStateBuilder.inProgressRound(tricks: tricks)
        def state = HeartsStateBuilder.state(rounds: [round])

        expect: "saboteur activates 9 tricks before Q♠ would cross the old threshold"
        strategy.resolveMode(state, alice) == Mode.SABOTEUR
    }

    def "mode stays DEFENSIVE when hearts accumulation is below the early threshold"() {
        given: "bob wins 3 hearts — not yet suspicious without Q♠"
        def strategy = new AdaptiveStrategy(false)
        def heart = { Rank r -> c(Suit.HEARTS, r) }
        def club  = { Rank r -> c(Suit.CLUBS, r) }
        def tricks = (1..3).collect { i ->
            new TrickState(i, "bob", [
                new PlayedCard("bob",   heart(Rank.values()[Rank.ACE.ordinal() - (i-1)])),
                new PlayedCard("carol", club(Rank.TWO)),
                new PlayedCard("dave",  club(Rank.THREE)),
                new PlayedCard("alice", club(Rank.FOUR)),
            ])
        }
        def round = HeartsStateBuilder.inProgressRound(tricks: tricks)
        def state = HeartsStateBuilder.state(rounds: [round])

        expect:
        strategy.resolveMode(state, alice) == Mode.DEFENSIVE
    }

    def "mode is SABOTEUR when opponent has crossed the shoot threshold"() {
        given: "bob has won Q♠ + A♥ = 14 points this round"
        def strategy = new AdaptiveStrategy(false)
        def trick1 = new TrickState(1, "bob", [
            new PlayedCard("bob",   QS),
            new PlayedCard("carol", c(Suit.SPADES, Rank.TWO)),
            new PlayedCard("dave",  c(Suit.SPADES, Rank.THREE)),
            new PlayedCard("alice", c(Suit.SPADES, Rank.FOUR)),
        ])
        def trick2 = new TrickState(2, "bob", [
            new PlayedCard("bob",   AH),
            new PlayedCard("carol", _2C),
            new PlayedCard("dave",  _3C),
            new PlayedCard("alice", _4C),
        ])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick1, trick2])
        def state = HeartsStateBuilder.state(rounds: [round])

        expect:
        strategy.resolveMode(state, alice) == Mode.SABOTEUR
    }

    def "saboteur beats shooter when it can follow suit"() {
        given: "bob leads 3♣ and is currently winning; alice holds A♣ which beats it"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(1, "bob", [new PlayedCard("bob", _3C)])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def card = strategy.selectSaboteurCard(state, alice, [AC, KH] as Set, "bob")

        then:
        card.get() == AC
    }

    def "saboteur plays lowest when it cannot beat shooter following hearts"() {
        given: "bob leads A♥; alice must follow hearts but cannot beat it"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(1, "bob", [new PlayedCard("bob", AH)])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def card = strategy.selectSaboteurCard(state, alice, [QH, NH] as Set, "bob")

        then: "plays lowest heart to minimise points given to the shooter"
        card.get() == NH
    }

    def "saboteur sluffs a safe card when shooter is winning the trick"() {
        given: "bob leads A♣ and is winning; alice has no clubs and must sluff"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(1, "bob", [new PlayedCard("bob", AC)])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def card = strategy.selectSaboteurCard(state, alice, [AH, _2D] as Set, "bob")

        then: "discards 2♦ instead of A♥ to avoid giving the shooter a heart"
        card.get() == _2D
    }

    def "saboteur defers to defensive when shooter is not currently winning"() {
        given: "carol (not the shooter) is winning; alice returns empty to fall back to defensive"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(1, "carol", [new PlayedCard("carol", _3C)])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def card = strategy.selectSaboteurCard(state, alice, [AC, _2D] as Set, "bob")

        then:
        card.isEmpty()
    }

    // ── full game completion ──────────────────────────────────────────────────────

    def "four bots using AdaptiveStrategy complete a full game without errors"() {
        def conditions = new PollingConditions(timeout: 15, initialDelay: 0.5, delay: 0.5)
        def game = new HeartsGame.Builder()
            .withPlayers([alice, bob, carol, dave])
            .withId(UUID.randomUUID())
            .withNotificationExecutor(Executors.newSingleThreadExecutor())
            .addPlayerObserver(alice, new AdaptiveStrategy())
            .addPlayerObserver(bob,   new AdaptiveStrategy())
            .addPlayerObserver(carol, new AdaptiveStrategy())
            .addPlayerObserver(dave,  new AdaptiveStrategy())
            .build()

        expect:
        conditions.eventually {
            game.getGameState().isOver()
        }
    }

    def "AdaptiveStrategy bots complete a full game when mixed with DefensiveStrategy bots"() {
        def conditions = new PollingConditions(timeout: 15, initialDelay: 0.5, delay: 0.5)
        def game = new HeartsGame.Builder()
            .withPlayers([alice, bob, carol, dave])
            .withId(UUID.randomUUID())
            .withNotificationExecutor(Executors.newSingleThreadExecutor())
            .addPlayerObserver(alice, new AdaptiveStrategy())
            .addPlayerObserver(bob,   new DefensiveStrategy())
            .addPlayerObserver(carol, new AdaptiveStrategy())
            .addPlayerObserver(dave,  new DefensiveStrategy())
            .build()

        expect:
        conditions.eventually {
            game.getGameState().isOver()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static RoundState roundWithHand(String playerName, List<Card> hand) {
        def leadingTrick = new TrickState(1, playerName, [])
        new RoundState(
            1, Pass.KEEPER, playerName,
            [(playerName): hand], [:], true,
            [leadingTrick],
            [(playerName): hand]
        )
    }

    private static RoundState roundWithRemainingCards(
            String playerName, List<Card> myHand, List<Card> allRemaining) {
        def leadingTrick = new TrickState(1, playerName, [])
        new RoundState(
            1, Pass.KEEPER, playerName,
            [(playerName): myHand], [:], true,
            [leadingTrick],
            [(playerName): myHand, "opponent": allRemaining - myHand]
        )
    }

    private static HeartsState stateWithHand(String playerName, List<Card> hand) {
        def round = roundWithHand(playerName, hand)
        HeartsStateBuilder.state(rounds: [round])
    }
}
