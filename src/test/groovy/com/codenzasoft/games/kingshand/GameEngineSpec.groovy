package com.codenzasoft.games.kingshand

import spock.lang.Specification

/**
 * Behavior tests for {@link GameEngine}: phase/season advancement, table mutations, season-trigger
 * pass, and deck/draw/discard/hand pile mutations. Pure-data invariants on {@link GameState} are
 * covered by {@link GameStateSpec}.
 */
class GameEngineSpec extends Specification {

    // ----- construction -----

    def "constructing an engine with a null state throws"() {
        when:
        new GameEngine(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "state cannot be null"
    }

    def "engine exposes the state it wraps"() {
        setup:
        def state = GameState.newState()

        expect:
        new GameEngine(state).state.is(state)
    }

    // ----- season / phase progression -----

    def "advancing the season changes current season correctly"() {
        setup:
        def state = GameState.newState()
        state.curSeason = startSeason
        def engine = new GameEngine(state)

        when:
        engine.advanceSeason()

        then:
        state.curSeason == expectedSeason

        where:
        startSeason   || expectedSeason
        Season.SPRING || Season.SUMMER
        Season.SUMMER || Season.FALL
        Season.FALL   || Season.WINTER
        Season.WINTER || Season.SPRING
    }

    def "advancing past winter increments the year"() {
        setup:
        def state = GameState.builder().curSeason(Season.WINTER).year(3).build()
        def engine = new GameEngine(state)

        when:
        engine.advanceSeason()

        then:
        state.curSeason == Season.SPRING
        state.year == 4
    }

    def "advancing a non-winter season does not change the year"() {
        setup:
        def state = GameState.builder().curSeason(startSeason).year(7).build()
        def engine = new GameEngine(state)

        when:
        engine.advanceSeason()

        then:
        state.year == 7

        where:
        startSeason << [Season.SPRING, Season.SUMMER, Season.FALL]
    }

    def "advancing the play phase advances both the season and the phase"() {
        setup:
        def state = GameState.builder().curPhase(Phase.PLAY).curSeason(Season.SPRING).build()
        def engine = new GameEngine(state)

        when:
        engine.advancePhase()

        then:
        state.curPhase == Phase.PLAY.next()
        state.curSeason == Season.SUMMER
    }

    def "advancing a non-play phase does not change the season"() {
        setup:
        def state = GameState.builder().curPhase(startPhase).curSeason(Season.SPRING).build()
        def engine = new GameEngine(state)

        when:
        engine.advancePhase()

        then:
        state.curPhase == startPhase.next()
        state.curSeason == Season.SPRING

        where:
        startPhase << [Phase.ADD, Phase.RANDOM_EVENT]
    }

    // ----- season trigger pass -----

    def "only recurring effects are triggered when the season advances"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)

        def card = new Card(
                0,
                0,
                [
                        new Effect(ResourceTarget.POPULATION, popModifier, popValueType, popFreq),
                        new Effect(ResourceTarget.PRODUCTION, prodModifier, prodValueType, prodFreq)
                ]
        )

        engine.addCard(Season.SPRING, card)

        when:
        engine.triggerCurrentSeasonTable()

        then:
        state.population == expectedPopulation
        state.production == expectedProduction

        where:
        popModifier | popValueType      | popFreq                 | prodModifier | prodValueType     | prodFreq                 || expectedPopulation | expectedProduction
        10          | ValueType.INTEGER | FrequencyType.SINGLE    | 20           | ValueType.INTEGER | FrequencyType.RECURRING  || 5                  | 120
        10          | ValueType.INTEGER | FrequencyType.RECURRING | 20           | ValueType.INTEGER | FrequencyType.SINGLE     || 15                 | 100
        10          | ValueType.INTEGER | FrequencyType.SINGLE    | 20           | ValueType.INTEGER | FrequencyType.SINGLE     || 5                  | 100
        10          | ValueType.INTEGER | FrequencyType.RECURRING | 20           | ValueType.INTEGER | FrequencyType.RECURRING  || 15                 | 120
    }

    def "card with recurring effects on all three resources fires every one of them"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)

        def card = new Card(
                0,
                0,
                [
                        new Effect(ResourceTarget.POPULATION, 1, ValueType.INTEGER, FrequencyType.RECURRING),
                        new Effect(ResourceTarget.PRODUCTION, 5, ValueType.INTEGER, FrequencyType.RECURRING),
                        new Effect(ResourceTarget.FOOD, 3, ValueType.INTEGER, FrequencyType.RECURRING)
                ]
        )

        engine.addCard(Season.SPRING, card)

        when:
        engine.triggerCurrentSeasonTable()

        then:
        state.population == 6     // 5 + 1
        state.production == 105   // 100 + 5
        state.food == 53          // 50 + 3
    }

    def "single-frequency effects do not fire on the season trigger"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)

        def card = new Card(
                0,
                0,
                [new Effect(ResourceTarget.FOOD, 10, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        state.board.get(Season.SUMMER).add(card)

        when:
        engine.advanceSeason()

        then:
        state.food == 50    // unchanged because the effect is SINGLE
    }

    // ----- table mutations -----

    def "addCard appends to the requested season's table"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)
        def card = new Card(0, 0, [])

        when:
        engine.addCard(Season.FALL, card)

        then:
        state.board.get(Season.FALL) == [card]
    }

    def "addCard throws when the table is full"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)
        def card = new Card(0, 0, [])
        GameEngine.MAX_TABLE_SIZE.times { engine.addCard(Season.SPRING, card) }

        when:
        engine.addCard(Season.SPRING, card)

        then:
        def e = thrown(IllegalStateException)
        e.message == "SPRING table is full"
    }

    def "removeCard removes the card from the requested season's table"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)
        def card = new Card(0, 0, [])
        engine.addCard(Season.WINTER, card)

        when:
        engine.removeCard(Season.WINTER, card)

        then:
        state.board.get(Season.WINTER) == []
    }

    // ----- pile mutations: addToDeck -----

    def "addToDeck places the single card in the correct season's draw pile"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)

        def card = new Card(
                0,
                0,
                [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        when:
        engine.addToDeck(Season.SPRING, card)

        then:
        state.drawPile.get(Season.SPRING) == [card]
        state.drawPile.get(Season.SUMMER).isEmpty()
        state.drawPile.get(Season.FALL).isEmpty()
        state.drawPile.get(Season.WINTER).isEmpty()
    }

    def "addToDeck adds multiple cards to the correct season's draw pile"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)

        def card1 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        def card2 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.PRODUCTION, 10, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        when:
        engine.addToDeck(Season.SUMMER, [card1, card2])

        then:
        state.drawPile.get(Season.SUMMER).size() == 2
        state.drawPile.get(Season.SUMMER).contains(card1)
        state.drawPile.get(Season.SUMMER).contains(card2)
        state.drawPile.get(Season.SPRING).isEmpty()
        state.drawPile.get(Season.FALL).isEmpty()
        state.drawPile.get(Season.WINTER).isEmpty()
    }

    def "addToDeck (single) throws when season is null"() {
        setup:
        def engine = new GameEngine(GameState.newState())
        def card = new Card(0, 0, [])

        when:
        engine.addToDeck(null, card)

        then:
        def e = thrown(IllegalStateException)
        e.message == "season cannot be null"
    }

    def "addToDeck (multi) throws when season is null"() {
        setup:
        def engine = new GameEngine(GameState.newState())

        when:
        engine.addToDeck(null, [new Card(0, 0, [])])

        then:
        def e = thrown(IllegalStateException)
        e.message == "season cannot be null"
    }

    def "addToDeck (multi) throws when card list is null"() {
        setup:
        def engine = new GameEngine(GameState.newState())

        when:
        engine.addToDeck(Season.SPRING, (List<Card>) null)

        then:
        def e = thrown(IllegalStateException)
        e.message == "cards cannot be null"
    }

    // ----- pile mutations: draw -----

    def "draw takes cards from the draw pile and puts them in the hand"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)

        def card1 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        def card2 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.PRODUCTION, 10, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        state.drawPile.get(Season.SPRING).add(card1)
        state.drawPile.get(Season.SPRING).add(card2)

        when:
        def drawnCards = engine.draw(Season.SPRING, 1)

        then:
        drawnCards.size() == 1
        drawnCards.contains(card1)

        state.hand.size() == 1
        state.hand.contains(card1)

        state.drawPile.get(Season.SPRING).size() == 1
        state.drawPile.get(Season.SPRING).contains(card2)
    }

    def "draw can draw multiple cards"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)

        def card1 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        def card2 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.PRODUCTION, 10, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        def card3 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.POPULATION, 1, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        state.drawPile.get(Season.SUMMER).add(card1)
        state.drawPile.get(Season.SUMMER).add(card2)
        state.drawPile.get(Season.SUMMER).add(card3)

        when:
        def drawnCards = engine.draw(Season.SUMMER, 2)

        then:
        drawnCards.size() == 2
        drawnCards.contains(card1)
        drawnCards.contains(card2)

        state.hand.size() == 2
        state.hand.contains(card1)
        state.hand.contains(card2)

        state.drawPile.get(Season.SUMMER).size() == 1
        state.drawPile.get(Season.SUMMER).contains(card3)
    }

    def "draw reshuffles the discard pile into the draw pile when draw pile is empty"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)

        def card1 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        def card2 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.PRODUCTION, 10, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        state.discardPile.get(Season.FALL).add(card1)
        state.discardPile.get(Season.FALL).add(card2)

        when:
        def drawnCards = engine.draw(Season.FALL, 1)

        then:
        drawnCards.size() == 1
        state.hand.size() == 1

        state.discardPile.get(Season.FALL).size() == 0
        state.drawPile.get(Season.FALL).size() == 1

        state.hand.get(0) == card1 || state.hand.get(0) == card2
    }

    def "draw stops when no cards are available"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)

        when:
        def drawnCards = engine.draw(Season.WINTER, 5)

        then:
        drawnCards.size() == 0
        state.hand.size() == 0
        state.drawPile.get(Season.WINTER).size() == 0
        state.discardPile.get(Season.WINTER).size() == 0
    }

    def "draw throws exception when season is null"() {
        setup:
        def engine = new GameEngine(GameState.newState())

        when:
        engine.draw(null, 1)

        then:
        def e = thrown(IllegalStateException)
        e.message == "season cannot be null"
    }

    def "draw throws exception when amount is negative"() {
        setup:
        def engine = new GameEngine(GameState.newState())

        when:
        engine.draw(Season.SPRING, -1)

        then:
        def e = thrown(IllegalStateException)
        e.message == "cannot draw a negative number of cards"
    }

    // ----- pile mutations: discardHand -----

    def "discardHand moves the hand into the correct discard pile"() {
        setup:
        def state = GameState.newState()
        def engine = new GameEngine(state)

        def card1 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        def card2 = new Card(
                0,
                0,
                [new Effect(ResourceTarget.PRODUCTION, 10, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        state.hand.add(card1)
        state.hand.add(card2)

        when:
        engine.discardHand(Season.SPRING)

        then:
        state.hand.size() == 0

        state.discardPile.get(Season.SPRING).size() == 2
        state.discardPile.get(Season.SPRING).contains(card1)
        state.discardPile.get(Season.SPRING).contains(card2)

        state.discardPile.get(Season.SUMMER).size() == 0
        state.discardPile.get(Season.FALL).size() == 0
        state.discardPile.get(Season.WINTER).size() == 0
    }

    def "discardHand throws exception when season is null"() {
        setup:
        def engine = new GameEngine(GameState.newState())

        when:
        engine.discardHand(null)

        then:
        def e = thrown(IllegalStateException)
        e.message == "season cannot be null"
    }
}
