package com.codenzasoft.games.kingshand

import spock.lang.Specification

class EffectSpec extends Specification {

    def "test that population updates correctly with an integer modifier"() {
        setup:
        def popEff = new Effect(2, ValueType.INTEGER, FrequencyType.SINGLE)

        when:
        def result = popEff.nextValue(2)

        then:
        result == 4
    }
}