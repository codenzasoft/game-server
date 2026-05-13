package com.codenzasoft.games.kingshand

import spock.lang.Specification

class PhaseSpec extends Specification {

    def "test that phases advance in the correct order"() {
        expect:
        phase.next() == nextPhase

        where:
        phase              || nextPhase
        Phase.ADD          || Phase.RANDOM_EVENT
        Phase.RANDOM_EVENT || Phase.PLAY
        Phase.PLAY         || Phase.ADD
    }
}