package com.codenzasoft.games.kingshand

import spock.lang.Specification

/**
 * Pure-data invariants for {@link GameState}: initial values, builder defaults, and the
 * preconditions enforced by {@code Builder#build}. Engine behavior (phase/season advancement,
 * table mutations, season triggers, pile mutations) is covered by {@link GameEngineSpec}.
 */
class GameStateSpec extends Specification {

    def "game starts in spring year 1 with initial values"() {
        when:
        GameState state = GameState.newState()

        then:
        state.time.season == Season.SPRING
        state.time.year == 1
        state.resources.population == 100
        state.resources.production == 1000
        state.resources.food == 750
        state.board.size() == 4
    }

    def "fresh state has empty board, drawPile, discardPile for every season and an empty hand"() {
        when:
        def state = GameState.newState()

        then:
        state.hand.isEmpty()
        Season.values().each { s ->
            assert state.board.get(s) == []
            assert state.drawPile.get(s) == []
            assert state.discardPile.get(s) == []
        }
    }

    def "fresh state starts in the ADD phase"() {
        expect:
        GameState.newState().time.phase == Phase.ADD
    }

    def "test that builder throws exception when current season is missing"() {
        setup:
        def builder = GameState.builder()
                .season(null)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == "missing current season"
    }

    def "test that builder throws exception when current phase is missing"() {
        setup:
        def builder = GameState.builder()
                .phase(null)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == "missing current phase"
    }

    def "test that builder throws exception when population is negative"() {
        setup:
        def builder = GameState.builder()
                .population(population)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == expected_msg

        where:
        population || expected_msg
        -1.0       || "population cannot be negative"
        -10.5      || "population cannot be negative"
        -100.0     || "population cannot be negative"
    }

    def "test that builder throws exception when production is negative"() {
        setup:
        def builder = GameState.builder()
                .production(production)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == expected_msg

        where:
        production || expected_msg
        -1.0       || "production cannot be negative"
        -5.5       || "production cannot be negative"
        -100.0     || "production cannot be negative"
    }

    def "test that builder throws exception when food is negative"() {
        setup:
        def builder = GameState.builder()
                .food(food)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == expected_msg

        where:
        food   || expected_msg
        -1.0   || "food cannot be negative"
        -5.5   || "food cannot be negative"
        -100.0 || "food cannot be negative"
    }

    def "test that builder throws exception when year is negative"() {
        setup:
        def builder = GameState.builder()
                .year(year)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == expected_msg

        where:
        year || expected_msg
        -1   || "year cannot be negative"
        -2   || "year cannot be negative"
        -100 || "year cannot be negative"
    }

    def "test that builder allows zero values"() {
        setup:
        def state = GameState.builder()
                .population(0.0)
                .production(0.0)
                .food(0.0)
                .year(0)
                .build()

        expect:
        state.resources.population == 0.0
        state.resources.production == 0.0
        state.resources.food == 0.0
        state.time.year == 0.0
    }

    def "getCurrentTable returns the table for the current season"() {
        setup:
        def state = GameState.builder().season(Season.FALL).build()
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        state.board.get(Season.FALL).add(card)

        expect:
        state.getCurrentTable().is(state.board.get(Season.FALL))
        state.getCurrentTable() == [card]
    }

    // ----- tableContains -----

    def "tableContains returns true when the card is on the current season's table"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def state = GameState.builder().season(Season.SPRING).table(Season.SPRING, [card]).build()

        expect:
        state.tableContains(card)
    }

    def "tableContains returns false when the card is not on the current season's table"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def state = GameState.builder().season(Season.SPRING).build()

        expect:
        !state.tableContains(card)
    }

    def "tableContains checks the current season's table only, not other seasons"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def state = GameState.builder()
                .season(Season.SPRING)
                .table(Season.SUMMER, [card])   // card is on SUMMER, not SPRING
                .build()

        expect:
        !state.tableContains(card)
    }

    // ----- replaceTableCard -----

    def "replaceTableCard swaps the matched card with the replacement"() {
        setup:
        def original    = new Card("TEST", 1, 10, FrequencyType.RECURRING, [])
        def replacement = new Card("TEST", 1, 10, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 20, ValueType.INTEGER)])
        def state = GameState.builder().season(Season.FALL).table(Season.FALL, [original]).build()

        when:
        state.replaceTableCard(original, replacement)

        then:
        state.getCurrentTable() == [replacement]
    }

    def "replaceTableCard replaces only the first matching card when duplicates exist"() {
        setup:
        def card  = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def other = new Card("TEST", 1, 10, FrequencyType.RECURRING, [])
        def state = GameState.builder()
                .season(Season.WINTER)
                .table(Season.WINTER, [card, other, card])
                .build()

        when:
        state.replaceTableCard(card, other)

        then:
        state.getCurrentTable() == [other, other, card]   // only index 0 replaced
    }

    def "replaceTableCard throws when the card is not on the table"() {
        setup:
        def state = GameState.builder().season(Season.SPRING).build()

        when:
        state.replaceTableCard(new Card("TEST", 0, 0, FrequencyType.RECURRING, []), new Card("TEST", 1, 5, FrequencyType.RECURRING, []))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "card not found on current table"
    }

    // ----- addToTable -----

    def "addToTable appends the card to the current season's table"() {
        setup:
        def card  = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def state = GameState.builder().season(Season.SUMMER).build()

        when:
        state.addToTable(card)

        then:
        state.getCurrentTable() == [card]
        state.board.get(Season.SPRING).isEmpty()
    }

    // ----- removeFromTable -----

    def "removeFromTable removes the first matching card from the current season's table"() {
        setup:
        def card  = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def other = new Card("TEST", 1, 10, FrequencyType.RECURRING, [])
        def state = GameState.builder()
                .season(Season.SPRING)
                .table(Season.SPRING, [card, other])
                .build()

        when:
        state.removeFromTable(card)

        then:
        state.getCurrentTable() == [other]
    }

    // ----- removeFromTableAt -----

    def "removeFromTableAt removes the card at the given slot index"() {
        setup:
        def first  = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def second = new Card("TEST", 1, 10, FrequencyType.RECURRING, [])
        def state = GameState.builder()
                .season(Season.FALL)
                .table(Season.FALL, [first, second])
                .build()

        when:
        state.removeFromTableAt(0)

        then:
        state.getCurrentTable() == [second]
    }

    // ----- discardHand -----

    def "discardHand moves the hand into the current season's discard pile"() {
        setup:
        def card1 = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER)])
        def card2 = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.PRODUCTION, 10, ValueType.INTEGER)])
        def state = GameState.builder().season(Season.SPRING).build()
        state.hand.add(card1)
        state.hand.add(card2)

        when:
        state.discardHand()

        then:
        state.hand.isEmpty()
        state.discardPile.get(Season.SPRING).size() == 2
        state.discardPile.get(Season.SPRING).containsAll([card1, card2])
        state.discardPile.get(Season.SUMMER).isEmpty()
        state.discardPile.get(Season.FALL).isEmpty()
        state.discardPile.get(Season.WINTER).isEmpty()
    }

    def "discardHand uses curSeason — discards to the correct pile when season is not SPRING"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.SINGLE, [])
        def state = GameState.builder().season(Season.WINTER).build()
        state.hand.add(card)

        when:
        state.discardHand()

        then:
        state.hand.isEmpty()
        state.discardPile.get(Season.WINTER) == [card]
        state.discardPile.get(Season.SPRING).isEmpty()
    }

    // ----- discardFromTable -----

    def "discardFromTable removes the card from the table and adds it to the current season's discard pile"() {
        setup:
        def card  = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.FOOD, 10, ValueType.INTEGER)])
        def other = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def state = GameState.builder()
                .season(Season.SUMMER)
                .table(Season.SUMMER, [card, other])
                .build()

        when:
        state.discardFromTable(card)

        then:
        state.getCurrentTable() == [other]
        state.discardPile.get(Season.SUMMER) == [card]
        state.discardPile.get(Season.SPRING).isEmpty()
        state.discardPile.get(Season.FALL).isEmpty()
        state.discardPile.get(Season.WINTER).isEmpty()
    }
}
