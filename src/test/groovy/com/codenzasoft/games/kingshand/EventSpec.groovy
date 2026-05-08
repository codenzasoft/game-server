package com.codenzasoft.games.kingshand

import spock.lang.Specification

class EventSpec extends Specification {

    def "event constructor defensively copies the options list"() {
        setup:
        def options = new ArrayList<EventOption>()
        options.add(new EventOption("opt", []))

        def event = new Event("e1", Season.SPRING, 1, "desc", options)

        when:
        options.clear()

        then:
        event.options().size() == 1
        event.options().get(0).description() == "opt"
    }

    def "event with null options gets an empty list"() {
        when:
        def event = new Event("e1", Season.SPRING, 1, "desc", null)

        then:
        event.options() == []
    }

    def "event options list is unmodifiable"() {
        setup:
        def event = new Event("e1", Season.SPRING, 1, "desc", [new EventOption("opt", [])])

        when:
        event.options().add(new EventOption("other", []))

        then:
        thrown(UnsupportedOperationException)
    }

    def "event option constructor defensively copies the effects list"() {
        setup:
        def effects = new ArrayList<Effect>()
        effects.add(new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER, FrequencyType.SINGLE))

        def option = new EventOption("opt", effects)

        when:
        effects.clear()

        then:
        option.effects().size() == 1
    }

    def "event option with null effects gets an empty list"() {
        when:
        def option = new EventOption("opt", null)

        then:
        option.effects() == []
    }

    def "event option uses the same effect data shape as cards"() {
        setup:
        def effect = new Effect(ResourceTarget.POPULATION, 2, ValueType.INTEGER, FrequencyType.RECURRING)
        def option = new EventOption("opt", [effect])

        expect:
        option.effects().get(0) == effect
    }
}
