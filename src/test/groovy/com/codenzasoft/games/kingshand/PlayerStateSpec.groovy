package com.codenzasoft.games.kingshand

import spock.lang.Specification

/**
 * Per-kingdom data invariants for {@link PlayerState}: table/hand/pile mutations. Because a player no
 * longer owns the clock, every season-scoped operation takes an explicit {@link Season}. These tests
 * were previously part of {@code GameStateSpec}, before the match/kingdom split.
 */
class PlayerStateSpec extends Specification {

    def "newPlayer starts with initial resources and empty seasonal piles"() {
        when:
        def p = PlayerState.newPlayer()

        then:
        p.resources.population == 100
        p.resources.production == 1000
        p.resources.food == 750
        p.hand.isEmpty()
        Season.values().each { s ->
            assert p.getBoard().get(s) == []
            assert p.getDrawPile().get(s) == []
            assert p.getDiscardPile().get(s) == []
        }
    }

    // ----- getTable -----

    def "getTable returns the table for the given season"() {
        setup:
        def p = PlayerState.newPlayer()
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        p.getBoard().get(Season.FALL).add(card)

        expect:
        p.getTable(Season.FALL).is(p.getBoard().get(Season.FALL))
        p.getTable(Season.FALL) == [card]
    }

    // ----- tableContains -----

    def "tableContains returns true when the card is on that season's table"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def p = PlayerState.builder().table(Season.SPRING, [card]).build()

        expect:
        p.tableContains(Season.SPRING, card)
    }

    def "tableContains returns false when the card is not on that season's table"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def p = PlayerState.newPlayer()

        expect:
        !p.tableContains(Season.SPRING, card)
    }

    def "tableContains checks only the named season's table"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def p = PlayerState.builder().table(Season.SUMMER, [card]).build()

        expect:
        !p.tableContains(Season.SPRING, card)
        p.tableContains(Season.SUMMER, card)
    }

    // ----- replaceTableCard -----

    def "replaceTableCard swaps the matched card with the replacement"() {
        setup:
        def original    = new Card("TEST", 1, 10, FrequencyType.RECURRING, [])
        def replacement = new Card("TEST", 1, 10, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 20)])
        def p = PlayerState.builder().table(Season.FALL, [original]).build()

        when:
        p.replaceTableCard(Season.FALL, original, replacement)

        then:
        p.getTable(Season.FALL) == [replacement]
    }

    def "replaceTableCard replaces only the first matching card when duplicates exist"() {
        setup:
        def card  = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def other = new Card("TEST", 1, 10, FrequencyType.RECURRING, [])
        def p = PlayerState.builder().table(Season.WINTER, [card, other, card]).build()

        when:
        p.replaceTableCard(Season.WINTER, card, other)

        then:
        p.getTable(Season.WINTER) == [other, other, card]   // only index 0 replaced
    }

    def "replaceTableCard throws when the card is not on the table"() {
        setup:
        def p = PlayerState.newPlayer()

        when:
        p.replaceTableCard(Season.SPRING, new Card("TEST", 0, 0, FrequencyType.RECURRING, []), new Card("TEST", 1, 5, FrequencyType.RECURRING, []))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "card not found on current table"
    }

    // ----- addToTable -----

    def "addToTable appends the card to the given season's table"() {
        setup:
        def card  = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def p = PlayerState.newPlayer()

        when:
        p.addToTable(Season.SUMMER, card)

        then:
        p.getTable(Season.SUMMER) == [card]
        p.getTable(Season.SPRING).isEmpty()
    }

    // ----- removeFromTable -----

    def "removeFromTable removes the first matching card from that season's table"() {
        setup:
        def card  = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def other = new Card("TEST", 1, 10, FrequencyType.RECURRING, [])
        def p = PlayerState.builder().table(Season.SPRING, [card, other]).build()

        when:
        p.removeFromTable(Season.SPRING, card)

        then:
        p.getTable(Season.SPRING) == [other]
    }

    // ----- removeFromTableAt -----

    def "removeFromTableAt removes the card at the given slot index"() {
        setup:
        def first  = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def second = new Card("TEST", 1, 10, FrequencyType.RECURRING, [])
        def p = PlayerState.builder().table(Season.FALL, [first, second]).build()

        when:
        p.removeFromTableAt(Season.FALL, 0)

        then:
        p.getTable(Season.FALL) == [second]
    }

    // ----- discardHand -----

    def "discardHand moves the hand into the given season's discard pile"() {
        setup:
        def card1 = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.FOOD, 5)])
        def card2 = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.PRODUCTION, 10)])
        def p = PlayerState.newPlayer()
        p.hand.add(card1)
        p.hand.add(card2)

        when:
        p.discardHand(Season.SPRING)

        then:
        p.hand.isEmpty()
        p.getDiscardPile().get(Season.SPRING).size() == 2
        p.getDiscardPile().get(Season.SPRING).containsAll([card1, card2])
        p.getDiscardPile().get(Season.SUMMER).isEmpty()
    }

    def "discardHand discards to the correct pile when season is not SPRING"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.SINGLE, [])
        def p = PlayerState.newPlayer()
        p.hand.add(card)

        when:
        p.discardHand(Season.WINTER)

        then:
        p.hand.isEmpty()
        p.getDiscardPile().get(Season.WINTER) == [card]
        p.getDiscardPile().get(Season.SPRING).isEmpty()
    }

    // ----- discardFromTable -----

    def "discardFromTable removes from the table and adds to that season's discard pile"() {
        setup:
        def card  = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.FOOD, 10)])
        def other = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def p = PlayerState.builder().table(Season.SUMMER, [card, other]).build()

        when:
        p.discardFromTable(Season.SUMMER, card)

        then:
        p.getTable(Season.SUMMER) == [other]
        p.getDiscardPile().get(Season.SUMMER) == [card]
        p.getDiscardPile().get(Season.SPRING).isEmpty()
    }
}
