package com.codenzasoft.games.kingshand.strategies

import spock.lang.Specification

/**
 * Smoke + balance specs for the self-play harness. These are the "does it actually survive ordinary
 * games?" checks the brief cares about most: a mirror match should run for years without both bots
 * starving out immediately, and the survival-aware bot should clearly outlast the greedy one.
 */
class KhBotArenaSpec extends Specification {

    def "beam-search bot outlasts the greedy bot"() {
        setup:
        def arena = new KhBotArena(
                { new KhBeamSearchStrategy(KhBotConfig.defaults()) },
                { new KhHeuristicStrategy() },
                12)

        when:
        def series = arena.playSeries(1L, 6)

        then: "the greedy bot starves far more often than the survival-aware bot"
        series.seat1StarvationRate() > series.seat0StarvationRate()

        and: "and the beam bot wins at least as many matches as it loses"
        series.seat0Wins() >= series.seat1Wins()
    }

    def "beam-search mirror match runs for years without an immediate double-starvation"() {
        setup:
        def arena = new KhBotArena(
                { new KhBeamSearchStrategy(KhBotConfig.defaults()) },
                { new KhBeamSearchStrategy(KhBotConfig.defaults()) },
                15)

        when:
        def result = arena.playGame(42L)

        then: "the match reached at least the second year (nobody starved out of the gate)"
        result.finalYear() >= 2
    }
}
