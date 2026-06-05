package com.codenzasoft.games.kingshand

import spock.lang.Specification

class EffectSpec extends Specification {

    def "INTEGER effects add the modifier to the current value"() {
        setup:
        def effect = new Effect(ResourceTarget.POPULATION, modifier, ValueType.INTEGER)

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
        def effect = new Effect(ResourceTarget.PRODUCTION, modifier, ValueType.PERCENTAGE)

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

    def "a card-targeting effect carries its target resource and adjacentOnly flag"() {
        setup:
        def target = new CardTarget(ResourceTarget.FOOD, true)

        when:
        def effect = new Effect(target, 4, ValueType.INTEGER)

        then:
        effect.target() == target
        (effect.target() as CardTarget).targetResource() == ResourceTarget.FOOD
        (effect.target() as CardTarget).adjacentOnly() == true
    }

    // ----- Effect.upgrade -----

    def "upgrade returns a new effect with the modifier scaled by the factor"() {
        setup:
        def effect = new Effect(ResourceTarget.FOOD, 10, ValueType.INTEGER)

        when:
        def upgraded = effect.upgrade(2)

        then:
        upgraded.modifier() == 20.0
    }

    def "upgrade preserves the target and valueType"() {
        setup:
        def effect = new Effect(ResourceTarget.PRODUCTION, 5, ValueType.PERCENTAGE)

        when:
        def upgraded = effect.upgrade(3)

        then:
        upgraded.target()    == ResourceTarget.PRODUCTION
        upgraded.valueType() == ValueType.PERCENTAGE
        upgraded.modifier()  == 15.0
    }

    def "upgrade does not mutate the original effect"() {
        setup:
        def original = new Effect(ResourceTarget.FOOD, 8, ValueType.INTEGER)

        when:
        original.upgrade(10)

        then:
        original.modifier() == 8.0
    }

    // ----- Card.amplify -----

    def "amplify returns a new card with every effect's modifier scaled by the factor"() {
        setup:
        def card = new Card("TEST", 2, 30, FrequencyType.RECURRING, [
                new Effect(ResourceTarget.FOOD, 10, ValueType.INTEGER),
                new Effect(ResourceTarget.PRODUCTION, 5, ValueType.INTEGER)
        ])

        when:
        def amplified = card.amplify(2)

        then:
        amplified.effects()[0].modifier() == 20.0
        amplified.effects()[1].modifier() == 10.0
    }

    def "amplify preserves the card's staff and production requirements"() {
        setup:
        def card = new Card("TEST", 3, 60, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 10, ValueType.INTEGER)])

        when:
        def amplified = card.amplify(2)

        then:
        amplified.staffRequirement() == 3.0
        amplified.productionRequirement() == 60.0
    }

    def "amplify preserves the card's frequencyType"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.FOOD, 10, ValueType.INTEGER)])

        when:
        def amplified = card.amplify(2)

        then:
        amplified.frequencyType() == FrequencyType.SINGLE
    }

    def "amplify does not mutate the original card"() {
        setup:
        def card = new Card("TEST", 2, 30, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 10, ValueType.INTEGER)])

        when:
        card.amplify(2)

        then:
        card.effects()[0].modifier() == 10.0
    }
}
