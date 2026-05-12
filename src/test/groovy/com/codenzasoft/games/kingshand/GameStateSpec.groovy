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
        state.curSeason == Season.SPRING
        state.year == 1
        state.population == 5
        state.production == 100
        state.food == 50
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
        GameState.newState().curPhase == Phase.ADD
    }

    def "test that builder throws exception when current season is missing"() {
        setup:
        def builder = GameState.builder()
                .curSeason(null)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == "missing current season"
    }

    def "test that builder throws exception when current phase is missing"() {
        setup:
        def builder = GameState.builder()
                .curPhase(null)

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
        state.getPopulation() == 0.0
        state.getProduction() == 0.0
        state.getFood() == 0.0
        state.getYear() == 0.0
    }

    def "getCurrentTable returns the table for the current season"() {
        setup:
        def state = GameState.builder().curSeason(Season.FALL).build()
        def card = new Card(0, 0, [])
        state.board.get(Season.FALL).add(card)

        expect:
        state.getCurrentTable().is(state.board.get(Season.FALL))
        state.getCurrentTable() == [card]
    }

    // ----- tableContains -----

    def "tableContains returns true when the card is on the current season's table"() {
        setup:
        def card = new Card(0, 0, [])
        def state = GameState.builder().curSeason(Season.SPRING).table(Season.SPRING, [card]).build()

        expect:
        state.tableContains(card)
    }

    def "tableContains returns false when the card is not on the current season's table"() {
        setup:
        def card = new Card(0, 0, [])
        def state = GameState.builder().curSeason(Season.SPRING).build()

        expect:
        !state.tableContains(card)
    }

    def "tableContains checks the current season's table only, not other seasons"() {
        setup:
        def card = new Card(0, 0, [])
        def state = GameState.builder()
                .curSeason(Season.SPRING)
                .table(Season.SUMMER, [card])   // card is on SUMMER, not SPRING
                .build()

        expect:
        !state.tableContains(card)
    }

    // ----- replaceTableCard -----

    def "replaceTableCard swaps the matched card with the replacement"() {
        setup:
        def original    = new Card(1, 10, [])
        def replacement = new Card(1, 10, [new Effect(ResourceTarget.FOOD, 20, ValueType.INTEGER, FrequencyType.RECURRING)])
        def state = GameState.builder().curSeason(Season.FALL).table(Season.FALL, [original]).build()

        when:
        state.replaceTableCard(original, replacement)

        then:
        state.getCurrentTable() == [replacement]
    }

    def "replaceTableCard replaces only the first matching card when duplicates exist"() {
        setup:
        def card  = new Card(0, 0, [])
        def other = new Card(1, 10, [])
        def state = GameState.builder()
                .curSeason(Season.WINTER)
                .table(Season.WINTER, [card, other, card])
                .build()

        when:
        state.replaceTableCard(card, other)

        then:
        state.getCurrentTable() == [other, other, card]   // only index 0 replaced
    }

    def "replaceTableCard throws when the card is not on the table"() {
        setup:
        def state = GameState.builder().curSeason(Season.SPRING).build()

        when:
        state.replaceTableCard(new Card(0, 0, []), new Card(1, 5, []))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "card not found on current table"
    }

    // ----- addToTable -----

    def "addToTable appends the card to the current season's table"() {
        setup:
        def card  = new Card(0, 0, [])
        def state = GameState.builder().curSeason(Season.SUMMER).build()

        when:
        state.addToTable(card)

        then:
        state.getCurrentTable() == [card]
        state.board.get(Season.SPRING).isEmpty()
    }

    // ----- removeFromTable -----

    def "removeFromTable removes the first matching card from the current season's table"() {
        setup:
        def card  = new Card(0, 0, [])
        def other = new Card(1, 10, [])
        def state = GameState.builder()
                .curSeason(Season.SPRING)
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
        def first  = new Card(0, 0, [])
        def second = new Card(1, 10, [])
        def state = GameState.builder()
                .curSeason(Season.FALL)
                .table(Season.FALL, [first, second])
                .build()

        when:
        state.removeFromTableAt(0)

        then:
        state.getCurrentTable() == [second]
    }
}
