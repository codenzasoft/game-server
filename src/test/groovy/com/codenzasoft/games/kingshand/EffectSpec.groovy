package com.codenzasoft.games.kingshand

import spock.lang.Specification

class EffectSpec extends Specification {

    def "test that population updates correctly with an integer modifier"() {
        setup:
        def popEff = new Effect(modifier, valueType, freqType)

        when:
        def result = popEff.nextValue(currentValue)

        then:
        result == expectedResult

        where:
        currentValue    | modifier  | valueType             | freqType                  || expectedResult
        2               | 2         | ValueType.INTEGER     | FrequencyType.SINGLE      || 4
        3               | 2         | ValueType.INTEGER     | FrequencyType.SINGLE      || 5
        2               | 50        | ValueType.PERCENTAGE  | FrequencyType.SINGLE      || 3
    }
}