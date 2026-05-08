package com.codenzasoft.games.kingshand

import spock.lang.Specification

class EffectSpec extends Specification {

    def "INTEGER effects add the modifier to the current value"() {
        setup:
        def effect = new Effect(ResourceTarget.POPULATION, modifier, ValueType.INTEGER, FrequencyType.SINGLE)

        when:
        def result = effect.apply(currentValue)

        then:
        result == expected

        where:
        currentValue | modifier || expected
        2            | 2        || 4
        3            | 2        || 5
        5            | 2        || 7
        10           | -3       || 7
        0            | 0        || 0
        100          | -100     || 0
    }

    def "PERCENTAGE effects scale the current value by the modifier percent"() {
        setup:
        def effect = new Effect(ResourceTarget.PRODUCTION, modifier, ValueType.PERCENTAGE, FrequencyType.SINGLE)

        when:
        def result = effect.apply(currentValue)

        then:
        result == expected

        where:
        currentValue | modifier || expected
        2            | 50       || 3
        100          | 25       || 125
        200          | -50      || 100
        100          | 10       || 110
        250          | -20      || 200
    }

    def "a card-targeting effect carries its slot offset"() {
        setup:
        def target = new CardTarget(-1)

        when:
        def effect = new Effect(target, 1, ValueType.INTEGER, FrequencyType.RECURRING)

        then:
        effect.target() == target
        (effect.target() as CardTarget).slotOffset() == -1
    }
}
