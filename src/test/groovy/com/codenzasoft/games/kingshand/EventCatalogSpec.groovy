package com.codenzasoft.games.kingshand

import spock.lang.Specification

class EventCatalogSpec extends Specification {

    private static Event evt(String id, Season season, int minYear) {
        new Event(id, season, minYear, id, [new EventOption("opt", [])])
    }

    def "catalog returns per-season events for the requested season"() {
        setup:
        def spring = evt("s1", Season.SPRING, 1)
        def summer = evt("u1", Season.SUMMER, 1)
        def catalog = new EventCatalog(
                [(Season.SPRING): [spring], (Season.SUMMER): [summer]])

        expect:
        catalog.eligibleEvents(Season.SPRING, 1) == [spring]
        catalog.eligibleEvents(Season.SUMMER, 1) == [summer]
    }

    def "events with minYear greater than currentYear are filtered out"() {
        setup:
        def early = evt("early", Season.SPRING, 1)
        def late  = evt("late",  Season.SPRING, 5)
        def catalog = new EventCatalog([(Season.SPRING): [early, late]])

        expect:
        catalog.eligibleEvents(Season.SPRING, 1) == [early]
        catalog.eligibleEvents(Season.SPRING, 4) == [early]
        catalog.eligibleEvents(Season.SPRING, 5) == [early, late]
        catalog.eligibleEvents(Season.SPRING, 9) == [early, late]
    }

    def "winter events with minYear gating escalate as the year advances"() {
        setup:
        def early = evt("winter.early", Season.WINTER, 1)
        def mid   = evt("winter.mid",   Season.WINTER, 3)
        def late  = evt("winter.late",  Season.WINTER, 5)
        def catalog = new EventCatalog([(Season.WINTER): [early, mid, late]])

        expect:
        catalog.eligibleEvents(Season.WINTER, 1) == [early]
        catalog.eligibleEvents(Season.WINTER, 3) == [early, mid]
        catalog.eligibleEvents(Season.WINTER, 5) == [early, mid, late]
    }

    def "events for a season do not leak into other seasons"() {
        setup:
        def winterEvt = evt("w1", Season.WINTER, 1)
        def catalog = new EventCatalog([(Season.WINTER): [winterEvt]])

        expect:
        catalog.eligibleEvents(Season.SPRING, 10) == []
        catalog.eligibleEvents(Season.SUMMER, 10) == []
        catalog.eligibleEvents(Season.FALL,   10) == []
        catalog.eligibleEvents(Season.WINTER, 1)  == [winterEvt]
    }

    def "eligible events returns an immutable snapshot"() {
        setup:
        def catalog = new EventCatalog(
                [(Season.SPRING): [evt("s1", Season.SPRING, 1)]])

        when:
        catalog.eligibleEvents(Season.SPRING, 1).add(evt("x", Season.SPRING, 1))

        then:
        thrown(UnsupportedOperationException)
    }

    def "constructor handles null seasonal map by treating every season as empty"() {
        setup:
        def catalog = new EventCatalog(null)

        expect:
        Season.values().every { catalog.eligibleEvents(it, 99).isEmpty() }
    }

    def "constructor handles missing seasons in the seasonal map"() {
        setup:
        def catalog = new EventCatalog(
                [(Season.SPRING): [evt("s1", Season.SPRING, 1)]])

        expect:
        catalog.getSeasonalEvents(Season.SUMMER) == []
        catalog.getSeasonalEvents(Season.FALL)   == []
        catalog.getSeasonalEvents(Season.WINTER) == []
    }

    def "constructor defensively copies seasonal lists"() {
        setup:
        def list = new ArrayList<Event>()
        list.add(evt("s1", Season.SPRING, 1))
        def catalog = new EventCatalog([(Season.SPRING): list])

        when:
        list.clear()

        then:
        catalog.getSeasonalEvents(Season.SPRING).size() == 1
    }

    def "eligibleEvents throws when season is null"() {
        setup:
        def catalog = new EventCatalog([:])

        when:
        catalog.eligibleEvents(null, 1)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "season cannot be null"
    }

    def "default catalog populates events for every season"() {
        setup:
        def catalog = EventCatalog.defaultCatalog()

        expect:
        Season.values().every { !catalog.getSeasonalEvents(it).isEmpty() }
    }

    def "default catalog escalates winter events as the year advances"() {
        setup:
        def catalog = EventCatalog.defaultCatalog()

        expect:
        catalog.eligibleEvents(Season.WINTER, 1).size() < catalog.eligibleEvents(Season.WINTER, 5).size()
    }
}
