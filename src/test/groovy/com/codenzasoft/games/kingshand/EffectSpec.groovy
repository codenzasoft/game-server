package com.codenzasoft.games.kingshand

import spock.lang.Specification

class EffectSpec extends Specification {

    def "effects add the modifier to the current value"() {
        setup:
        def effect = new Effect(ResourceTarget.POPULATION, modifier)

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

    def "a card-targeting effect carries its target resource and adjacentOnly flag"() {
        setup:
        def target = new CardTarget(ResourceTarget.FOOD, true)

        when:
        def effect = new Effect(target, 4)

        then:
        effect.target() == target
        (effect.target() as CardTarget).targetResource() == ResourceTarget.FOOD
        (effect.target() as CardTarget).adjacentOnly() == true
    }

    // ----- Effect.upgrade -----

    def "upgrade returns a new effect with the modifier scaled by the factor"() {
        setup:
        def effect = new Effect(ResourceTarget.FOOD, 10)

        when:
        def upgraded = effect.upgrade(2)

        then:
        upgraded.modifier() == 20.0
    }

    def "upgrade preserves the target"() {
        setup:
        def effect = new Effect(ResourceTarget.PRODUCTION, 5)

        when:
        def upgraded = effect.upgrade(3)

        then:
        upgraded.target()   == ResourceTarget.PRODUCTION
        upgraded.modifier() == 15.0
    }

    def "upgrade does not mutate the original effect"() {
        setup:
        def original = new Effect(ResourceTarget.FOOD, 8)

        when:
        original.upgrade(10)

        then:
        original.modifier() == 8.0
    }

    // ----- Card.amplify -----

    def "amplify returns a new card with every effect's modifier scaled by the factor"() {
        setup:
        def card = new Card("TEST", 2, 30, FrequencyType.RECURRING, [
                new Effect(ResourceTarget.FOOD, 10),
                new Effect(ResourceTarget.PRODUCTION, 5)
        ])

        when:
        def amplified = card.amplify(2)

        then:
        amplified.effects()[0].modifier() == 20.0
        amplified.effects()[1].modifier() == 10.0
    }

    def "amplify preserves the card's staff and production requirements"() {
        setup:
        def card = new Card("TEST", 3, 60, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 10)])

        when:
        def amplified = card.amplify(2)

        then:
        amplified.staffRequirement() == 3.0
        amplified.productionRequirement() == 60.0
    }

    def "amplify preserves the card's frequencyType"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.FOOD, 10)])

        when:
        def amplified = card.amplify(2)

        then:
        amplified.frequencyType() == FrequencyType.SINGLE
    }

    def "amplify does not mutate the original card"() {
        setup:
        def card = new Card("TEST", 2, 30, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 10)])

        when:
        card.amplify(2)

        then:
        card.effects()[0].modifier() == 10.0
    }
}
