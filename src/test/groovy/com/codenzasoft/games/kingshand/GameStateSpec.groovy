package com.codenzasoft.games.kingshand

import spock.lang.Specification

class GameStateSpec extends Specification {

    def "game starts in spring year 1 with initial values"() {
        when:
        GameState state = GameState.newState()

        then:
        state.curSeason == Season.SPRING
        state.year == 1
        state.population == 5
        state.production == 100
        state.board.size() == 4
    }

    def "test that advancing season changes current season correctly"() {
        setup:
        def state = GameState.newState()
        state.curSeason = startSeason

        when:
        state.advanceSeason()

        then:
        state.curSeason == expectedSeason

        where:
        startSeason   || expectedSeason
        Season.SPRING || Season.SUMMER
        Season.SUMMER || Season.FALL
        Season.FALL   || Season.WINTER
        Season.WINTER || Season.SPRING
    }

    def "test only recurring effects are triggered"() {
        setup:
        def state = GameState.newState()

        def card = new Card(
                0,
                0,
                new Effect(popModifier, popValueType, popFreq),
                new Effect(prodModifier, prodValueType, prodFreq)
        )

        state.board.get(Season.SUMMER).add(card)

        when:
        state.advanceSeason()

        then:
        state.population == expectedPopulation
        state.production == expectedProduction

        where:
        popModifier | popValueType      | popFreq                 | prodModifier | prodValueType     | prodFreq                 || expectedPopulation | expectedProduction
        10          | ValueType.INTEGER | FrequencyType.SINGLE    | 20           | ValueType.INTEGER | FrequencyType.RECURRING  || 5                  | 120
        10          | ValueType.INTEGER | FrequencyType.RECURRING | 20           | ValueType.INTEGER | FrequencyType.SINGLE     || 15                 | 100
        10          | ValueType.INTEGER | FrequencyType.SINGLE    | 20           | ValueType.INTEGER | FrequencyType.SINGLE     || 5                  | 100
        10          | ValueType.INTEGER | FrequencyType.RECURRING    | 20           | ValueType.INTEGER | FrequencyType.RECURRING     || 15                  | 120
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
        -1.0   || "population cannot be negative"
        -10.5   || "population cannot be negative"
        -100.0 || "population cannot be negative"
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
        -1.0   || "production cannot be negative"
        -5.5   || "production cannot be negative"
        -100.0 || "production cannot be negative"

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
                .year(0)
                .build()

        expect:
        state.getPopulation() == 0.0
        state.getProduction() == 0.0
        state.getYear() == 0.0
    }
}
