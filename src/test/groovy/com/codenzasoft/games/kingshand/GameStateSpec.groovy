package com.codenzasoft.games.kingshand

import spock.lang.Specification

class GameStateSpec extends Specification {

    def "game starts in spring year 1 with initial values"() {
        when:
        GameState state = new GameState()

        then:
        state.curSeason == Season.SPRING
        state.year == 1
        state.population == 5
        state.production == 100
        state.board.size() == 4
    }
    
    def "test that advancing season changes current season correctly"() {
        setup:
        def state = new GameState()
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
        def state = new GameState()

        def card = new Card(
                0,
                0,
                new Effect(popModifier, popValueType, popFreq),
                new Effect(prodModifier, prodValueType, prodFreq)
        )

        state.board.get(1).cards.add(card)

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

}