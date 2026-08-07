package com.codenzasoft.games.kingshand

import spock.lang.Specification

/**
 * Match-level invariants for {@link GameState}: builder defaults/preconditions, the shared clock
 * (including the two-player "either kingdom unlocked GOLD" council rule), the multi-kingdom
 * structure, and match outcome (knock-out / victory / winner). Per-kingdom data behavior lives in
 * {@link PlayerStateSpec}; engine behavior in {@link GameEngineSpec}.
 */
class GameStateSpec extends Specification {

    def "game starts in spring year 1 with initial values"() {
        when:
        GameState state = GameState.newState()

        then:
        state.time.season == Season.SPRING
        state.time.year == 1
        state.getPlayer(0).resources.population == 100
        state.getPlayer(0).resources.production == 1000
        state.getPlayer(0).resources.food == 750
        state.getPlayer(0).getBoard().size() == 4
    }

    def "fresh single-player state has one kingdom in the ADD phase"() {
        when:
        def state = GameState.newState()

        then:
        state.playerCount() == 1
        state.time.phase == Phase.ADD
        state.getPlayer(0).hand.isEmpty()
        Season.values().each { s ->
            assert state.getPlayer(0).getBoard().get(s) == []
            assert state.getPlayer(0).getDrawPile().get(s) == []
            assert state.getPlayer(0).getDiscardPile().get(s) == []
        }
    }

    // ----- builder preconditions -----

    def "test that builder throws exception when current season is missing"() {
        setup:
        def builder = GameState.builder()
                .season(null)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == "missing current season"
    }

    def "test that builder throws exception when current phase is missing"() {
        setup:
        def builder = GameState.builder()
                .phase(null)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == "missing current phase"
    }

    def "test that builder throws exception when population is negative"() {
        setup:
        def builder = GameState.builder()
                .population(population)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == expected_msg

        where:
        population || expected_msg
        -1.0       || "population cannot be negative"
        -10.5      || "population cannot be negative"
        -100.0     || "population cannot be negative"
    }

    def "test that builder throws exception when production is negative"() {
        setup:
        def builder = GameState.builder()
                .production(production)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == expected_msg

        where:
        production || expected_msg
        -1.0       || "production cannot be negative"
        -5.5       || "production cannot be negative"
        -100.0     || "production cannot be negative"
    }

    def "test that builder throws exception when food is negative"() {
        setup:
        def builder = GameState.builder()
                .food(food)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == expected_msg

        where:
        food   || expected_msg
        -1.0   || "food cannot be negative"
        -5.5   || "food cannot be negative"
        -100.0 || "food cannot be negative"
    }

    def "test that builder throws exception when year is negative"() {
        setup:
        def builder = GameState.builder()
                .year(year)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == expected_msg

        where:
        year || expected_msg
        -1   || "year cannot be negative"
        -2   || "year cannot be negative"
        -100 || "year cannot be negative"
    }

    def "test that builder allows zero values"() {
        setup:
        def state = GameState.builder()
                .population(0.0)
                .production(0.0)
                .food(0.0)
                .year(0)
                .build()

        expect:
        state.getPlayer(0).resources.population == 0.0
        state.getPlayer(0).resources.production == 0.0
        state.getPlayer(0).resources.food == 0.0
        state.time.year == 0.0
    }

    // ----- two-player match structure -----

    def "newTwoPlayerState creates two independent kingdoms sharing one clock"() {
        when:
        GameState state = GameState.newTwoPlayerState()

        then:
        state.getPlayers().size() == 2
        state.playerCount() == 2
        state.getPlayer(0) !== state.getPlayer(1)
        state.getPlayer(0).getBoard() !== state.getPlayer(1).getBoard()
        state.getPlayer(0).getResources().population() == 100
        state.getPlayer(1).getResources().population() == 100
        state.time.season == Season.SPRING
        state.time.year == 1
    }

    def "mutating one seat's table does not affect the other seat"() {
        setup:
        def card  = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        GameState state = GameState.newTwoPlayerState()

        when:
        state = state.withPlayer(0, state.getPlayer(0).addToTable(Season.SPRING, card))

        then:
        state.getPlayer(0).getTable(Season.SPRING) == [card]
        state.getPlayer(1).getTable(Season.SPRING).isEmpty()
    }

    def "newMultiplayer rejects a player count below one"() {
        when:
        GameState.newMultiplayer(0)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "a match needs at least one player"
    }

    // ----- shared clock: two-player council rule -----

    def "advanceTime opens the council after Winter when EITHER kingdom has unlocked gold"() {
        setup:
        def withGold = PlayerState.builder().goldUnlocked(true).build()
        def noGold   = PlayerState.newPlayer()
        def state = GameState.multiPlayer([noGold, withGold], new TimeState(Season.WINTER, Phase.PLAY, 1))

        when:
        state = state.advanceTime()

        then:
        state.time.phase == Phase.COUNCIL_ADD
        state.time.season == Season.WINTER
    }

    def "advanceTime skips the council after Winter when NEITHER kingdom has gold"() {
        setup:
        def state = GameState.multiPlayer(
                [PlayerState.newPlayer(), PlayerState.newPlayer()],
                new TimeState(Season.WINTER, Phase.PLAY, 1))

        when:
        state = state.advanceTime()

        then:
        state.time.phase == Phase.ADD
        state.time.season == Season.SPRING
        state.time.year == 2
    }

    // ----- match outcome -----

    def "a fresh two-player match is not over and has no winner"() {
        expect:
        !GameState.newTwoPlayerState().isMatchOver()
        GameState.newTwoPlayerState().winnerSeat() == -1
    }

    def "a knocked-out seat ends the match and the survivor wins"() {
        setup:
        def alive     = PlayerState.newPlayer()
        def collapsed = PlayerState.builder().population(0.0).build()
        def state = GameState.multiPlayer([alive, collapsed], new TimeState(Season.SPRING, Phase.PLAY, 1))

        expect:
        state.isPlayerOut(1)
        !state.isPlayerOut(0)
        state.isMatchOver()
        state.winnerSeat() == 0
    }
}
