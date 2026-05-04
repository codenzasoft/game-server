package com.codenzasoft.games.kingshand

import spock.lang.Specification

class CardSpec extends Specification {

    def "test that card updates population correctly"() {
        setup:
        def popEffect = new Effect(modifier, valueType, freqType)
        def prodEffect = new Effect(0, ValueType.INTEGER, FrequencyType.SINGLE)
        def card = new Card(0, 0, popEffect, prodEffect)

        when:
        def result = card.nextPopulation(currentPopulation)

        then:
        result == expectedPopulation

        where:
        currentPopulation | modifier | valueType            | freqType                 || expectedPopulation
        5                 | 2        | ValueType.INTEGER    | FrequencyType.SINGLE     || 7
        10                | -3       | ValueType.INTEGER    | FrequencyType.SINGLE     || 7
        100               | 25       | ValueType.PERCENTAGE | FrequencyType.SINGLE     || 125
        200               | -50      | ValueType.PERCENTAGE | FrequencyType.SINGLE     || 100
    }

    def "test that card updates production correctly"() {
        setup:
        def popEffect = new Effect(0, ValueType.INTEGER, FrequencyType.SINGLE)
        def prodEffect = new Effect(modifier, valueType, freqType)
        def card = new Card(0, 0, popEffect, prodEffect)

        when:
        def result = card.nextProduction(currentProduction)

        then:
        result == expectedProduction

        where:
        currentProduction | modifier | valueType            | freqType                 || expectedProduction
        100               | 20       | ValueType.INTEGER    | FrequencyType.SINGLE     || 120
        100               | -30      | ValueType.INTEGER    | FrequencyType.SINGLE     || 70
        100               | 10       | ValueType.PERCENTAGE | FrequencyType.SINGLE     || 110
        250               | -20      | ValueType.PERCENTAGE | FrequencyType.SINGLE     || 200
    }
}