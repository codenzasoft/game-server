package com.codenzasoft.games.kingshand

import spock.lang.Specification

class GameStateSpec extends Specification {

    def "game starts in spring year 1 with initial values"() {
        when:
        GameState state = GameState.newState()

        then:
        state.curSeason == Season.SPRING
        state.year == 1
        state.population == 5
        state.production == 100
        state.food == 50
        state.board.size() == 4
    }

    def "test that advancing season changes current season correctly"() {
        setup:
        def state = GameState.newState()
        state.curSeason = startSeason

        when:
        state.advanceSeason()

        then:
        state.curSeason == expectedSeason

        where:
        startSeason   || expectedSeason
        Season.SPRING || Season.SUMMER
        Season.SUMMER || Season.FALL
        Season.FALL   || Season.WINTER
        Season.WINTER || Season.SPRING
    }

    def "test only recurring effects are triggered"() {
        setup:
        def state = GameState.newState()

        def card = new Card(
                0,
                0,
                [
                        new Effect(ResourceTarget.POPULATION, popModifier, popValueType, popFreq),
                        new Effect(ResourceTarget.PRODUCTION, prodModifier, prodValueType, prodFreq)
                ]
        )

        state.board.get(Season.SUMMER).add(card)

        when:
        state.advanceSeason()

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

    def "test card with recurring effects on all three resources fires every one of them"() {
        setup:
        def state = GameState.newState()

        def card = new Card(
                0,
                0,
                [
                        new Effect(ResourceTarget.POPULATION, 1, ValueType.INTEGER, FrequencyType.RECURRING),
                        new Effect(ResourceTarget.PRODUCTION, 5, ValueType.INTEGER, FrequencyType.RECURRING),
                        new Effect(ResourceTarget.FOOD, 3, ValueType.INTEGER, FrequencyType.RECURRING)
                ]
        )

        state.board.get(Season.SUMMER).add(card)

        when:
        state.advanceSeason()

        then:
        state.population == 6     // 5 + 1
        state.production == 105   // 100 + 5
        state.food == 53          // 50 + 3
    }

    def "test single-frequency effects do not fire on the season trigger"() {
        setup:
        def state = GameState.newState()

        def card = new Card(
                0,
                0,
                [new Effect(ResourceTarget.FOOD, 10, ValueType.INTEGER, FrequencyType.SINGLE)]
        )

        state.board.get(Season.SUMMER).add(card)

        when:
        state.advanceSeason()

        then:
        state.food == 50    // unchanged because the effect is SINGLE
    }

    def "test that builder throws exception when current season is missing"() {
        setup:
        def builder = GameState.builder()
                .curSeason(null)

        when:
        builder.build()

        then:
        def e = thrown(IllegalStateException)
        e.message == "missing current season"
    }

    def "test that builder throws exception when current phase is missing"() {
        setup:
        def builder = GameState.builder()
                .curPhase(null)

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
        -1.0   || "population cannot be negative"
        -10.5   || "population cannot be negative"
        -100.0 || "population cannot be negative"
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
        -1.0   || "production cannot be negative"
        -5.5   || "production cannot be negative"
        -100.0 || "production cannot be negative"

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
        state.getPopulation() == 0.0
        state.getProduction() == 0.0
        state.getFood() == 0.0
        state.getYear() == 0.0
    }

    def "test that addToDeck adds one card to the correct season deck"() {
    setup:
    def state = GameState.newState()

    def card = new Card(
            0,
            0,
            [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER, FrequencyType.SINGLE)]
    )

    when:
    state.addToDeck(Season.SPRING, card)

    then:
    state.deck.get(Season.SPRING).size() == 1
    state.deck.get(Season.SPRING).contains(card)
    state.deck.get(Season.SUMMER).size() == 0
    state.deck.get(Season.FALL).size() == 0
    state.deck.get(Season.WINTER).size() == 0
    }

    def "test that addToDeck adds multiple cards to the correct season deck"() {
    setup:
    def state = GameState.newState()

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
    state.addToDeck(Season.SUMMER, [card1, card2])

    then:
    state.deck.get(Season.SUMMER).size() == 2
    state.deck.get(Season.SUMMER).contains(card1)
    state.deck.get(Season.SUMMER).contains(card2)
    state.deck.get(Season.SPRING).size() == 0
    state.deck.get(Season.FALL).size() == 0
    state.deck.get(Season.WINTER).size() == 0
    }

    def "test that shuffleDeckIntoDrawPile moves deck cards into draw pile"() {
    setup:
    def state = GameState.newState()

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

    state.deck.get(Season.FALL).add(card1)
    state.deck.get(Season.FALL).add(card2)

    when:
    state.shuffleDeckIntoDrawPile(Season.FALL)

    then:
    state.deck.get(Season.FALL).size() == 0
    state.drawPile.get(Season.FALL).size() == 2
    state.drawPile.get(Season.FALL).contains(card1)
    state.drawPile.get(Season.FALL).contains(card2)
    }

    def "test that draw takes cards from draw pile and puts them in hand"() {
    setup:
    def state = GameState.newState()

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
    def drawnCards = state.draw(Season.SPRING, 1)

    then:
    drawnCards.size() == 1
    drawnCards.contains(card1)

    state.hand.size() == 1
    state.hand.contains(card1)

    state.drawPile.get(Season.SPRING).size() == 1
    state.drawPile.get(Season.SPRING).contains(card2)
    }

    def "test that draw can draw multiple cards"() {
    setup:
    def state = GameState.newState()

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
    def drawnCards = state.draw(Season.SUMMER, 2)

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

    def "test that draw reshuffles discard pile into draw pile when draw pile is empty"() {
    setup:
    def state = GameState.newState()

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
    def drawnCards = state.draw(Season.FALL, 1)

    then:
    drawnCards.size() == 1
    state.hand.size() == 1

    state.discardPile.get(Season.FALL).size() == 0
    state.drawPile.get(Season.FALL).size() == 1

    state.hand.get(0) == card1 || state.hand.get(0) == card2
    }

    def "test that draw stops when no cards are available"() {
    setup:
    def state = GameState.newState()

    when:
    def drawnCards = state.draw(Season.WINTER, 5)

    then:
    drawnCards.size() == 0
    state.hand.size() == 0
    state.drawPile.get(Season.WINTER).size() == 0
    state.discardPile.get(Season.WINTER).size() == 0
    }

    def "test that discardHand moves hand into the correct discard pile"() {
    setup:
    def state = GameState.newState()

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
    state.discardHand(Season.SPRING)

    then:
    state.hand.size() == 0

    state.discardPile.get(Season.SPRING).size() == 2
    state.discardPile.get(Season.SPRING).contains(card1)
    state.discardPile.get(Season.SPRING).contains(card2)

    state.discardPile.get(Season.SUMMER).size() == 0
    state.discardPile.get(Season.FALL).size() == 0
    state.discardPile.get(Season.WINTER).size() == 0
    }

    def "test that draw throws exception when season is null"() {
    setup:
    def state = GameState.newState()

    when:
    state.draw(null, 1)

    then:
    def e = thrown(IllegalStateException)
    e.message == "season cannot be null"
    }

    def "test that draw throws exception when amount is negative"() {
    setup:
    def state = GameState.newState()

    when:
    state.draw(Season.SPRING, -1)

    then:
    def e = thrown(IllegalStateException)
    e.message == "cannot draw a negative number of cards"
    }

    def "test that addToDeck throws exception when season is null"() {
    setup:
    def state = GameState.newState()

    def card = new Card(
            0,
            0,
            [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER, FrequencyType.SINGLE)]
    )

    when:
    state.addToDeck(null, card)

    then:
    def e = thrown(IllegalStateException)
    e.message == "season cannot be null"
    }

    def "test that discardHand throws exception when season is null"() {
    setup:
    def state = GameState.newState()

    when:
    state.discardHand(null)

    then:
    def e = thrown(IllegalStateException)
    e.message == "season cannot be null"
    }


}
