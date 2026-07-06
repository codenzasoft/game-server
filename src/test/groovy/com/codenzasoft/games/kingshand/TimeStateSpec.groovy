package com.codenzasoft.games.kingshand

import org.aspectj.apache.bcel.generic.RET
import spock.lang.Specification

class TimeStateSpec extends Specification {

    def "advancing the time works as expected"() {
        setup:
        def state = new TimeState(startSeason, startPhase, startYear)

        when:
        state = state.advance(ResourceState.empty())

        then:
        state.phase() == expectedPhase
        state.season() == expectedSeason
        state.year() == expectedYear

        where:
        startSeason   | startPhase  | startYear || expectedSeason    | expectedPhase    | expectedYear
        Season.SPRING | Phase.ADD   | 1         || Season.SPRING     | Phase.PLAY       | 1
        Season.SPRING | Phase.PLAY  | 1         || Season.SUMMER     | Phase.ADD        | 1
        Season.SUMMER | Phase.ADD   | 1         || Season.SUMMER     | Phase.PLAY       | 1
        Season.SUMMER | Phase.PLAY  | 1         || Season.FALL       | Phase.ADD        | 1
        Season.FALL   | Phase.ADD   | 1         || Season.FALL       | Phase.PLAY       | 1
        Season.FALL   | Phase.PLAY  | 1         || Season.WINTER     | Phase.ADD        | 1
        Season.WINTER | Phase.ADD   | 1         || Season.WINTER     | Phase.PLAY       | 1
        Season.WINTER | Phase.PLAY  | 1         || Season.SPRING     | Phase.ADD        | 2 // gold not unlocked — council is skipped
    }

    def "advancing the time after winter opens the council once gold is unlocked"() {
        setup:
        def resources = ResourceState.empty().withUnlocked(Set.of(ResourceTarget.GOLD))
        def state = new TimeState(startSeason, startPhase, startYear)

        when:
        state = state.advance(resources)

        then:
        state.phase() == expectedPhase
        state.season() == expectedSeason
        state.year() == expectedYear

        where:
        startSeason   | startPhase              | startYear || expectedSeason    | expectedPhase            | expectedYear
        Season.SPRING | Phase.PLAY              | 1         || Season.SUMMER     | Phase.ADD                | 1
        Season.SUMMER | Phase.PLAY              | 1         || Season.FALL       | Phase.ADD                | 1
        Season.FALL   | Phase.PLAY              | 1         || Season.WINTER     | Phase.ADD                | 1
        Season.WINTER | Phase.PLAY              | 1         || Season.WINTER     | Phase.COUNCIL_ADD        | 1
        Season.WINTER | Phase.COUNCIL_ADD       | 1         || Season.WINTER     | Phase.COUNCIL_MEETING    | 1
        Season.WINTER | Phase.COUNCIL_MEETING   | 1         || Season.SPRING     | Phase.ADD                | 2
    }
}
