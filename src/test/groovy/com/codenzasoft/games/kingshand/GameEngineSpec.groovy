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

    // ----- ADD phase: offerAddChoices -----

    def "offerAddChoices returns exactly three cards"() {
        setup:
        def state = GameState.builder().curPhase(Phase.ADD).build()
        def engine = new GameEngine(state)

        when:
        def choices = engine.offerAddChoices()

        then:
        choices.size() == 3
    }

    def "offerAddChoices returns only cards from the current season's catalog"() {
        setup:
        def catalog = CardCatalog.defaultCatalog()
        def state = GameState.builder().curPhase(Phase.ADD).curSeason(Season.SPRING).build()
        def engine = new GameEngine(state, catalog)

        when:
        def choices = engine.offerAddChoices()

        then:
        catalog.forSeason(Season.SPRING).containsAll(choices)
    }

    def "offerAddChoices draws from the correct season — not another season's pool"() {
        setup:
        def catalog = CardCatalog.defaultCatalog()
        def state = GameState.builder().curPhase(Phase.ADD).curSeason(Season.WINTER).build()
        def engine = new GameEngine(state, catalog)

        when:
        def choices = engine.offerAddChoices()

        then:
        catalog.forSeason(Season.WINTER).containsAll(choices)
        choices.every { !catalog.forSeason(Season.SPRING).contains(it) }
    }

    def "offerAddChoices stores choices in state and resets reroll count"() {
        setup:
        def state = GameState.builder().curPhase(Phase.ADD).rerollCount(2).build()
        def engine = new GameEngine(state)

        when:
        def choices = engine.offerAddChoices()

        then:
        state.addChoices == choices
        state.rerollCount == 0
    }

    def "offerAddChoices throws when phase is not ADD"() {
        setup:
        def state = GameState.builder().curPhase(Phase.RANDOM_EVENT).build()
        def engine = new GameEngine(state)

        when:
        engine.offerAddChoices()

        then:
        thrown(IllegalStateException)
    }

    // ----- ADD phase: pickCard -----

    def "pickCard shuffles the chosen card into the current season's draw pile"() {
        setup:
        def state = GameState.builder().curPhase(Phase.ADD).curSeason(Season.SPRING).build()
        def engine = new GameEngine(state)
        def card = CardCatalog.FARM

        when:
        engine.pickCard(card)

        then:
        state.drawPile.get(Season.SPRING).contains(card)
    }

    def "pickCard advances the phase to RANDOM_EVENT"() {
        setup:
        def state = GameState.builder().curPhase(Phase.ADD).build()
        def engine = new GameEngine(state)

        when:
        engine.pickCard(CardCatalog.FARM)

        then:
        state.curPhase == Phase.RANDOM_EVENT
    }

    def "pickCard clears addChoices and resets reroll count"() {
        setup:
        def state = GameState.builder()
                .curPhase(Phase.ADD)
                .rerollCount(1)
                .build()
        def engine = new GameEngine(state)
        // pre-populate some choices so we can verify they're cleared
        state.addChoices = [CardCatalog.FARM]

        when:
        engine.pickCard(CardCatalog.FARM)

        then:
        state.addChoices.isEmpty()
        state.rerollCount == 0
    }

    def "pickCard throws when phase is not ADD"() {
        setup:
        def state = GameState.builder().curPhase(Phase.PLAY).build()
        def engine = new GameEngine(state)

        when:
        engine.pickCard(CardCatalog.FARM)

        then:
        thrown(IllegalStateException)
    }

    // ----- ADD phase: rerollAddChoices -----

    def "rerollAddChoices charges 5 production for the first reroll"() {
        setup:
        def state = GameState.builder()
                .curPhase(Phase.ADD)
                .production(100.0)
                .build()
        def engine = new GameEngine(state)

        when:
        engine.rerollAddChoices()

        then:
        state.production == 95.0
        state.rerollCount == 1
    }

    def "rerollAddChoices cost escalates with each reroll within an ADD phase"() {
        setup:
        def state = GameState.builder()
                .curPhase(Phase.ADD)
                .production(200.0)
                .build()
        def engine = new GameEngine(state)

        when: "first reroll costs 5"
        engine.rerollAddChoices()

        then:
        state.production == 195.0
        state.rerollCount == 1

        when: "second reroll costs 10"
        engine.rerollAddChoices()

        then:
        state.production == 185.0
        state.rerollCount == 2

        when: "third reroll costs 15"
        engine.rerollAddChoices()

        then:
        state.production == 170.0
        state.rerollCount == 3
    }

    def "rerollAddChoices replaces the offered cards and updates state"() {
        setup:
        def state = GameState.builder()
                .curPhase(Phase.ADD)
                .production(100.0)
                .build()
        def engine = new GameEngine(state)

        when:
        def choices = engine.rerollAddChoices()

        then:
        choices.size() == 3
        CardCatalog.defaultCatalog().forSeason(Season.SPRING).containsAll(choices)
        state.addChoices == choices
    }

    def "rerollAddChoices throws when player cannot afford the cost"() {
        setup:
        def state = GameState.builder()
                .curPhase(Phase.ADD)
                .production(3.0)   // less than the first-reroll cost of 5
                .build()
        def engine = new GameEngine(state)

        when:
        engine.rerollAddChoices()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("not enough production to reroll")
    }

    def "rerollAddChoices does not charge or change state when player can't afford"() {
        setup:
        def state = GameState.builder()
                .curPhase(Phase.ADD)
                .production(4.0)
                .build()
        def engine = new GameEngine(state)

        when:
        engine.rerollAddChoices()

        then:
        thrown(IllegalStateException)
        state.production == 4.0      // unchanged
        state.rerollCount == 0       // unchanged
    }

    def "rerollAddChoices throws when phase is not ADD"() {
        setup:
        def state = GameState.builder().curPhase(Phase.RANDOM_EVENT).build()
        def engine = new GameEngine(state)

        when:
        engine.rerollAddChoices()

        then:
        thrown(IllegalStateException)
    }

    // ----- ADD phase: skipAdd -----

    def "skipAdd advances the phase to RANDOM_EVENT without adding a card"() {
        setup:
        def state = GameState.builder()
                .curPhase(Phase.ADD)
                .curSeason(Season.SPRING)
                .build()
        def engine = new GameEngine(state)

        when:
        engine.skipAdd()

        then:
        state.curPhase == Phase.RANDOM_EVENT
        state.drawPile.get(Season.SPRING).isEmpty()
        state.drawPile.get(Season.SUMMER).isEmpty()
        state.drawPile.get(Season.FALL).isEmpty()
        state.drawPile.get(Season.WINTER).isEmpty()
    }

    def "skipAdd clears addChoices and resets reroll count"() {
        setup:
        def state = GameState.builder()
                .curPhase(Phase.ADD)
                .rerollCount(2)
                .build()
        state.addChoices = [CardCatalog.SEEDS]
        def engine = new GameEngine(state)

        when:
        engine.skipAdd()

        then:
        state.addChoices.isEmpty()
        state.rerollCount == 0
    }

    def "skipAdd throws when phase is not ADD"() {
        setup:
        def state = GameState.builder().curPhase(Phase.PLAY).build()
        def engine = new GameEngine(state)

        when:
        engine.skipAdd()

        then:
        thrown(IllegalStateException)
    }
}
