package com.codenzasoft.games.kingshand

import spock.lang.Specification

class SeasonSpec extends Specification {

    def "test that seasons advance in the correct order"() {
        expect:
        season.next() == nextSeason

        where:
        season        || nextSeason
        Season.SPRING || Season.SUMMER
        Season.SUMMER || Season.FALL
        Season.FALL   || Season.WINTER
        Season.WINTER || Season.SPRING
    }
}