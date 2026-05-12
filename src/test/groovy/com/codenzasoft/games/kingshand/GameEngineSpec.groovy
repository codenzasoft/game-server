package com.codenzasoft.games.kingshand

import spock.lang.Specification

/**
 * Behavior tests for {@link GameEngine}: phase/season advancement, table mutations, season-trigger
 * pass, deck/draw/discard/hand pile mutations, and RANDOM_EVENT resolution (event rolling, option
 * effect application, year filtering, and winter magnitude scaling).
 *
 * <p>Pure-data invariants on {@link GameState} are covered by {@link GameStateSpec}.
 */
class GameEngineSpec extends Specification {

    // ----- helpers -----

    private static EventCatalog catalogWith(Season season, Event... events) {
        new EventCatalog([(season): events.toList()])
    }

    private static Event simpleEvent(String id, Season season, int minYear, List<Effect> effects = []) {
        new Event(id, season, minYear, id, [new EventOption("opt", effects)])
    }

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

    def "first call each ADD phase is free and stores choices in state"() {
        setup:
        def state = GameState.builder().curPhase(Phase.ADD).production(100.0).build()
        def engine = new GameEngine(state)

        when:
        def choices = engine.offerAddChoices()

        then:
        state.addChoices == choices
        state.rerollCount == 1        // incremented from 0
        state.production  == 100.0   // no charge on first call
    }

    def "second call costs 10 production (first reroll)"() {
        setup:
        def state = GameState.builder().curPhase(Phase.ADD).production(100.0).build()
        def engine = new GameEngine(state)
        engine.offerAddChoices()   // free first call

        when:
        engine.offerAddChoices()   // first paid reroll

        then:
        state.production == 90.0
        state.rerollCount == 2
    }

    def "reroll cost escalates with each subsequent call within the same ADD phase"() {
        setup:
        def state = GameState.builder().curPhase(Phase.ADD).production(200.0).build()
        def engine = new GameEngine(state)

        when: "free first offer"
        engine.offerAddChoices()

        then:
        state.production == 200.0
        state.rerollCount == 1

        when: "first reroll costs 10"
        engine.offerAddChoices()

        then:
        state.production == 190.0
        state.rerollCount == 2

        when: "second reroll costs 20"
        engine.offerAddChoices()

        then:
        state.production == 170.0
        state.rerollCount == 3

        when: "third reroll costs 30"
        engine.offerAddChoices()

        then:
        state.production == 140.0
        state.rerollCount == 4
    }

    def "offerAddChoices throws when player cannot afford a reroll"() {
        setup:
        def state = GameState.builder().curPhase(Phase.ADD).production(5.0).build()
        def engine = new GameEngine(state)
        engine.offerAddChoices()   // free first call; rerollCount is now 1

        when:
        engine.offerAddChoices()   // would cost 10, but only 5 available

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("not enough production to reroll")
    }

    def "offerAddChoices does not charge or advance reroll count when player can't afford"() {
        setup:
        def state = GameState.builder().curPhase(Phase.ADD).production(5.0).build()
        def engine = new GameEngine(state)
        engine.offerAddChoices()   // free; rerollCount == 1

        when:
        engine.offerAddChoices()   // would cost 10

        then:
        thrown(IllegalStateException)
        state.production  == 5.0   // unchanged
        state.rerollCount == 1     // unchanged
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

    // ----- RANDOM_EVENT: event rolling -----

    def "advancing to RANDOM_EVENT rolls an eligible event and stores it on the state"() {
        setup:
        def event = simpleEvent("spring.test", Season.SPRING, 1)
        def state = GameState.builder().curPhase(Phase.ADD).curSeason(Season.SPRING).year(1).build()
        def engine = new GameEngine(state, catalogWith(Season.SPRING, event))

        when:
        engine.advancePhase()   // ADD -> RANDOM_EVENT

        then:
        state.curPhase == Phase.RANDOM_EVENT
        engine.currentEvent() == event
    }

    def "currentEvent returns null when no eligible events exist for the current year"() {
        setup:
        // event not eligible until year 5; state is at year 1
        def event = simpleEvent("spring.late", Season.SPRING, 5)
        def state = GameState.builder().curPhase(Phase.ADD).curSeason(Season.SPRING).year(1).build()
        def engine = new GameEngine(state, catalogWith(Season.SPRING, event))

        when:
        engine.advancePhase()   // ADD -> RANDOM_EVENT

        then:
        engine.currentEvent() == null
    }

    def "year-filter: only events with minYear <= currentYear enter the roll pool"() {
        setup:
        def early = simpleEvent("spring.early", Season.SPRING, 1)
        def late  = simpleEvent("spring.late",  Season.SPRING, 5)
        // Use a seeded Random that always picks index 0; with a pool of [early] only early can appear
        def state = GameState.builder().curPhase(Phase.ADD).curSeason(Season.SPRING).year(3).build()
        def engine = new GameEngine(state, new EventCatalog([(Season.SPRING): [early, late]]), new Random(0))

        when:
        engine.advancePhase()   // ADD -> RANDOM_EVENT

        then:
        // 'late' has minYear 5 so it must be excluded; only 'early' is in the pool
        engine.currentEvent() == early
    }

    def "roll selects from all eligible events when multiple are in the pool"() {
        setup:
        def e1 = simpleEvent("w1", Season.WINTER, 1)
        def e2 = simpleEvent("w2", Season.WINTER, 1)
        def e3 = simpleEvent("w3", Season.WINTER, 1)
        def state = GameState.builder().curPhase(Phase.ADD).curSeason(Season.WINTER).year(1).build()
        // Run many times with different seeds and confirm every event can be selected
        def catalog = new EventCatalog([(Season.WINTER): [e1, e2, e3]])
        def seen = [] as Set

        when:
        (0..99).each { seed ->
            def s = GameState.builder().curPhase(Phase.ADD).curSeason(Season.WINTER).year(1).build()
            def eng = new GameEngine(s, catalog, new Random(seed))
            eng.advancePhase()
            seen << eng.currentEvent().id()
        }

        then:
        seen.containsAll(["w1", "w2", "w3"])
    }

    // ----- RANDOM_EVENT: applyEventOption — normal effect application -----

    def "applyEventOption applies a food bonus to state and advances to PLAY"() {
        setup:
        def option = new EventOption("Take food", [ResourceTarget.FOOD.once(15)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SPRING).year(1).food(50.0).build()
        def engine = new GameEngine(state, new EventCatalog([:]))

        when:
        engine.applyEventOption(option)

        then:
        state.food == 65.0
        state.curPhase == Phase.PLAY
    }

    def "applyEventOption applies a production penalty to state"() {
        setup:
        def option = new EventOption("Pay cost", [ResourceTarget.PRODUCTION.once(-10)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SUMMER).year(2).production(100.0).build()
        def engine = new GameEngine(state, new EventCatalog([:]))

        when:
        engine.applyEventOption(option)

        then:
        state.production == 90.0
        state.curPhase == Phase.PLAY
    }

    def "applyEventOption applies multiple effects from one option"() {
        setup:
        def option = new EventOption(
                "Plant aggressively",
                [ResourceTarget.FOOD.once(15), ResourceTarget.PRODUCTION.once(-5)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SPRING).year(1)
                .food(50.0).production(100.0).build()
        def engine = new GameEngine(state, new EventCatalog([:]))

        when:
        engine.applyEventOption(option)

        then:
        state.food == 65.0
        state.production == 95.0
        state.curPhase == Phase.PLAY
    }

    def "applyEventOption with an empty effect list still advances to PLAY"() {
        setup:
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SUMMER).year(1).build()
        def engine = new GameEngine(state, new EventCatalog([:]))

        when:
        engine.applyEventOption(new EventOption("Do nothing", []))

        then:
        state.curPhase == Phase.PLAY
        state.food       == 50.0  // unchanged
        state.production == 100.0 // unchanged
        state.population == 5.0   // unchanged
    }

    def "applyEventOption throws when option is null"() {
        setup:
        def state = GameState.builder().curPhase(Phase.RANDOM_EVENT).curSeason(Season.SPRING).year(1).build()
        def engine = new GameEngine(state, new EventCatalog([:]))

        when:
        engine.applyEventOption(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "option cannot be null"
    }

    // ----- RANDOM_EVENT: winter magnitude scaling (KH-6) -----

    def "winter event effects are scaled by the current year"() {
        setup:
        // Base modifier is -10; at year 3 the applied delta should be -30
        def option = new EventOption("Endure", [ResourceTarget.FOOD.once(-10)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.WINTER).year(3).food(50.0).build()
        def engine = new GameEngine(state, new EventCatalog([:]))

        when:
        engine.applyEventOption(option)

        then:
        state.food == 50.0 + (-10 * 3)   // 20.0
        state.curPhase == Phase.PLAY
    }

    def "winter scaling applies to every resource target in the option"() {
        setup:
        def option = new EventOption(
                "Slaughter livestock",
                [ResourceTarget.FOOD.once(10), ResourceTarget.PRODUCTION.once(-10)])
        // year 2: food +20, production -20
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.WINTER).year(2)
                .food(50.0).production(100.0).build()
        def engine = new GameEngine(state, new EventCatalog([:]))

        when:
        engine.applyEventOption(option)

        then:
        state.food       == 50.0  + (10  * 2)   // 70.0
        state.production == 100.0 + (-10 * 2)   // 80.0
    }

    def "winter scaling at year 1 leaves the magnitude unchanged"() {
        setup:
        def option = new EventOption("Burn firewood", [ResourceTarget.PRODUCTION.once(-5)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.WINTER).year(1).production(100.0).build()
        def engine = new GameEngine(state, new EventCatalog([:]))

        when:
        engine.applyEventOption(option)

        then:
        state.production == 100.0 + (-5 * 1)   // 95.0
    }

    def "non-winter event effects are NOT year-scaled"() {
        setup:
        def option = new EventOption("Trade", [ResourceTarget.PRODUCTION.once(10)])
        def state = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.SUMMER).year(5).production(100.0).build()
        def engine = new GameEngine(state, new EventCatalog([:]))

        when:
        engine.applyEventOption(option)

        then:
        // year is 5 but scaling must NOT apply outside winter; delta is flat +10
        state.production == 110.0
    }

    def "year-scaling applies independently per season: fall and spring unscaled, winter scaled"() {
        setup:
        def option = new EventOption("opt", [ResourceTarget.FOOD.once(10)])

        when: "fall (year 4) — no scaling"
        def fallState = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.FALL).year(4).food(50.0).build()
        new GameEngine(fallState, new EventCatalog([:])).applyEventOption(option)

        then:
        fallState.food == 60.0

        when: "winter (year 4) — scaled by 4"
        def winterState = GameState.builder()
                .curPhase(Phase.RANDOM_EVENT).curSeason(Season.WINTER).year(4).food(50.0).build()
        new GameEngine(winterState, new EventCatalog([:])).applyEventOption(option)

        then:
        winterState.food == 50.0 + (10 * 4)   // 90.0
    }

    // ----- construction guard -----

    def "constructing an engine with a null catalog throws"() {
        when:
        new GameEngine(GameState.newState(), null, new Random())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "catalog cannot be null"
    }

    def "constructing an engine with a null rng throws"() {
        when:
        new GameEngine(GameState.newState(), new EventCatalog([:]), null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "rng cannot be null"
    }
}
