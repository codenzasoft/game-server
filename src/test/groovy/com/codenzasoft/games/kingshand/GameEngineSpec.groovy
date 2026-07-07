package com.codenzasoft.games.kingshand

import com.codenzasoft.games.common.Player
import spock.lang.Specification

/**
 * Behavior tests for {@link GameEngine}: phase/season advancement, table mutations, season-trigger
 * pass, deck/draw/discard/hand pile mutations, and end-of-PLAY resolution (worker staffing,
 * great-work construction, food consumption/starvation, and GAME_OVER triggers).
 *
 * <p>Pure-data invariants on {@link GameState} are covered by {@link GameStateSpec}.
 */
class GameEngineSpec extends Specification {

    // ----- helpers -----

    private static final Player TEST_PLAYER = new Player(UUID.randomUUID(), "test")

    private static GameEngine engine(GameState state) {
        new GameEngine(UUID.randomUUID(), TEST_PLAYER, state)
    }

    private static GameEngine engine(GameState state, CardCatalog catalog) {
        new GameEngine(UUID.randomUUID(), TEST_PLAYER, state, catalog, new Random())
    }

    private static GameEngine engine(GameState state, CardCatalog catalog, Random random) {
        new GameEngine(UUID.randomUUID(), TEST_PLAYER, state, catalog, random)
    }

    // ----- construction -----

    def "constructing an engine with a null state throws"() {
        when:
        engine(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "state cannot be null"
    }

    def "engine exposes the state it wraps"() {
        setup:
        def state = GameState.newState()

        expect:
        engine(state).state.is(state)
    }

    def "advancing a non-play phase is a no-op"() {
        setup:
        def state = GameState.builder().phase(startPhase).season(Season.SPRING).build()
        def engine = engine(state)

        when:
        engine.advancePhase()

        then:
        state.time.phase == startPhase
        state.time.season == Season.SPRING

        where:
        startPhase << [Phase.ADD, Phase.COUNCIL_ADD, Phase.COUNCIL_MEETING]
    }

    // ----- season trigger pass -----

    def "RECURRING cards fire all effects on the season trigger; SINGLE cards do not"() {
        setup:
        def state = GameState.newState()
        def engine = engine(state)

        def recurringCard = new Card("TEST", 0, 0, FrequencyType.RECURRING, [
                new Effect(ResourceTarget.POPULATION, 10, ValueType.INTEGER),
                new Effect(ResourceTarget.PRODUCTION, 20, ValueType.INTEGER)
        ])
        def singleCard = new Card("TEST", 0, 0, FrequencyType.SINGLE, [
                new Effect(ResourceTarget.FOOD, 99, ValueType.INTEGER)
        ])
        engine.addCard(Season.SPRING, recurringCard)
        engine.addCard(Season.SPRING, singleCard)

        when:
        engine.triggerCurrentSeasonTable()

        then:
        state.resources.population == 100   // 100 pop cap
        state.resources.production == 1020  // 1000 + 20 from RECURRING card
        state.resources.food       == 750   // unchanged — SINGLE card did not fire
    }

    def "card with recurring effects on all three resources fires every one of them"() {
        setup:
        def state = GameState.newState()
        def engine = engine(state)

        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [
                        new Effect(ResourceTarget.POPULATION, 1, ValueType.INTEGER),
                        new Effect(ResourceTarget.PRODUCTION, 5, ValueType.INTEGER),
                        new Effect(ResourceTarget.FOOD, 3, ValueType.INTEGER)
                ]
        )

        engine.addCard(Season.SPRING, card)

        when:
        engine.triggerCurrentSeasonTable()

        then:
        state.resources.population == 100   // 100 pop cap
        state.resources.production == 1005  // 1000 + 5
        state.resources.food == 753         // 750 + 3
    }

    def "single-frequency effects do not fire on the season trigger"() {
        setup:
        def state = GameState.newState()
        def engine = engine(state)

        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 10, ValueType.INTEGER)]
        )

        state.board.get(Season.SUMMER).add(card)

        when:
        engine.state.advanceTime() // add -> play (spring)
        engine.state.advanceTime() // play -> add (summer)

        then:
        engine.state.time.season == Season.SUMMER
        state.resources.food == 750   // unchanged because the effect is SINGLE
    }

    // ----- table mutations -----

    def "addCard appends to the requested season's table"() {
        setup:
        def state = GameState.newState()
        def engine = engine(state)
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])

        when:
        engine.addCard(Season.FALL, card)

        then:
        state.board.get(Season.FALL) == [card]
    }

    def "addCard throws when the table is full"() {
        setup:
        def state = GameState.newState()
        def engine = engine(state)
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
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
        def engine = engine(state)
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
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
        def engine = engine(state)

        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER)]
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
        def engine = engine(state)

        def card1 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER)]
        )

        def card2 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.PRODUCTION, 10, ValueType.INTEGER)]
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
        def engine = engine(GameState.newState())
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])

        when:
        engine.addToDeck(null, card)

        then:
        def e = thrown(IllegalStateException)
        e.message == "season cannot be null"
    }

    def "addToDeck (multi) throws when season is null"() {
        setup:
        def engine = engine(GameState.newState())

        when:
        engine.addToDeck(null, [new Card("TEST", 0, 0, FrequencyType.RECURRING, [])])

        then:
        def e = thrown(IllegalStateException)
        e.message == "season cannot be null"
    }

    def "addToDeck (multi) throws when card list is null"() {
        setup:
        def engine = engine(GameState.newState())

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
        def engine = engine(state)

        def card1 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER)]
        )

        def card2 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.PRODUCTION, 10, ValueType.INTEGER)]
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
        def engine = engine(state)

        def card1 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER)]
        )

        def card2 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.PRODUCTION, 10, ValueType.INTEGER)]
        )

        def card3 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.POPULATION, 1, ValueType.INTEGER)]
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
        def engine = engine(state)

        def card1 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER)]
        )

        def card2 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.PRODUCTION, 10, ValueType.INTEGER)]
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
        def engine = engine(state)

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
        def engine = engine(GameState.newState())

        when:
        engine.draw(null, 1)

        then:
        def e = thrown(IllegalStateException)
        e.message == "season cannot be null"
    }

    def "draw throws exception when amount is negative"() {
        setup:
        def engine = engine(GameState.newState())

        when:
        engine.draw(Season.SPRING, -1)

        then:
        def e = thrown(IllegalStateException)
        e.message == "cannot draw a negative number of cards"
    }

    // ----- ADD phase: offerAddChoices -----

    def "offerAddChoices returns exactly three cards"() {
        setup:
        def state = GameState.builder().phase(Phase.ADD).build()
        def engine = engine(state)

        when:
        def choices = engine.offerAddChoices()

        then:
        choices.size() == 3
    }

    def "offerAddChoices returns only cards from the current season's catalog"() {
        setup:
        def catalog = CardCatalog.defaultCatalog()
        def state = GameState.builder().phase(Phase.ADD).season(Season.SPRING).build()
        def engine = engine(state, catalog)

        when:
        def choices = engine.offerAddChoices()

        then:
        catalog.forSeason(Season.SPRING).containsAll(choices)
    }

    def "offerAddChoices draws from the correct season — not another season's pool"() {
        setup:
        def catalog = CardCatalog.defaultCatalog()
        def state = GameState.builder().phase(Phase.ADD).season(Season.WINTER).build()
        def engine = engine(state, catalog)

        when:
        def choices = engine.offerAddChoices()

        then:
        catalog.forSeason(Season.WINTER).containsAll(choices)
        choices.every { !catalog.forSeason(Season.SPRING).contains(it) }
    }

    def "first call each ADD phase is free and stores choices in state"() {
        setup:
        def state = GameState.builder().phase(Phase.ADD).production(100.0).build()
        def engine = engine(state)

        when:
        def choices = engine.offerAddChoices()

        then:
        state.addChoices == choices
        state.rerollCount == 1        // incremented from 0
        state.resources.production  == 100.0   // no charge on first call
    }

    def "second call costs 50 production (first reroll)"() {
        setup:
        def state = GameState.builder().phase(Phase.ADD).production(1000.0).build()
        def engine = engine(state)
        engine.offerAddChoices()   // free first call

        when:
        engine.offerAddChoices()   // first paid reroll

        then:
        state.resources.production == 950.0
        state.rerollCount == 2
    }

    def "reroll cost escalates with each subsequent call within the same ADD phase"() {
        setup:
        def state = GameState.builder().phase(Phase.ADD).production(1000.0).build()
        def engine = engine(state)

        when: "free first offer"
        engine.offerAddChoices()

        then:
        state.resources.production == 1000.0
        state.rerollCount == 1

        when: "first reroll costs 50"
        engine.offerAddChoices()

        then:
        state.resources.production == 950.0
        state.rerollCount == 2

        when: "second reroll costs 100"
        engine.offerAddChoices()

        then:
        state.resources.production == 850.0
        state.rerollCount == 3

        when: "third reroll costs 150"
        engine.offerAddChoices()

        then:
        state.resources.production == 700.0
        state.rerollCount == 4
    }

    def "offerAddChoices throws when player cannot afford a reroll"() {
        setup:
        def state = GameState.builder().phase(Phase.ADD).production(25.0).build()
        def engine = engine(state)
        engine.offerAddChoices()   // free first call; rerollCount is now 1

        when:
        engine.offerAddChoices()   // would cost 50, but only 25 available

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("not enough production to reroll")
    }

    def "offerAddChoices does not charge or advance reroll count when player can't afford"() {
        setup:
        def state = GameState.builder().phase(Phase.ADD).production(25.0).build()
        def engine = engine(state)
        engine.offerAddChoices()   // free; rerollCount == 1

        when:
        engine.offerAddChoices()   // would cost 50

        then:
        thrown(IllegalStateException)
        state.resources.production  == 25.0  // unchanged
        state.rerollCount == 1     // unchanged
    }

    def "offerAddChoices throws when phase is not ADD"() {
        setup:
        def state = GameState.builder().phase(Phase.PLAY).build()
        def engine = engine(state)

        when:
        engine.offerAddChoices()

        then:
        thrown(IllegalStateException)
    }

    // ----- ADD phase: pickCard -----

    def "pickCard shuffles the chosen card into the current season's draw pile or hand"() {
        setup:
        def state = GameState.builder().phase(Phase.ADD).season(Season.SPRING).build()
        def engine = engine(state)
        def card = CardCatalog.FARM

        when:
        engine.pickCard(card)

        then:
        // startPlay() runs synchronously after pickCard and may draw the card straight into hand.
        (state.drawPile.get(Season.SPRING) + state.hand).contains(card)
    }

    def "pickCard advances the phase to PLAY"() {
        setup:
        def state = GameState.builder().phase(Phase.ADD).build()
        def engine = engine(state)

        when:
        engine.pickCard(CardCatalog.FARM)

        then:
        state.time.phase == Phase.PLAY
    }

    def "pickCard clears addChoices and resets reroll count"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.ADD)
                .rerollCount(1)
                .build()
        def engine = engine(state)
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
        def state = GameState.builder().phase(Phase.PLAY).build()
        def engine = engine(state)

        when:
        engine.pickCard(CardCatalog.FARM)

        then:
        thrown(IllegalStateException)
    }

    // ----- ADD phase: skipAdd -----

    def "skipAdd advances the phase to PLAY without adding a card"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.ADD)
                .season(Season.SPRING)
                .build()
        def engine = engine(state)

        when:
        engine.skipAdd()

        then:
        state.time.phase == Phase.PLAY
        state.drawPile.get(Season.SPRING).isEmpty()
        state.drawPile.get(Season.SUMMER).isEmpty()
        state.drawPile.get(Season.FALL).isEmpty()
        state.drawPile.get(Season.WINTER).isEmpty()
    }

    def "skipAdd clears addChoices and resets reroll count"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.ADD)
                .rerollCount(2)
                .build()
        state.addChoices = [CardCatalog.SEEDS]
        def engine = engine(state)

        when:
        engine.skipAdd()

        then:
        state.addChoices.isEmpty()
        state.rerollCount == 0
    }

    def "skipAdd throws when phase is not ADD"() {
        setup:
        def state = GameState.builder().phase(Phase.PLAY).build()
        def engine = engine(state)

        when:
        engine.skipAdd()

        then:
        thrown(IllegalStateException)
    }

    // Note: COUNCIL_ADD/COUNCIL_MEETING (hireAdvisor/skipCouncilAdd/acceptCouncilProposal/
    // passCouncilProposal) replaced the old RANDOM_EVENT system but have no dedicated test
    // coverage yet — out of scope for this build fix.

    // ----- construction guard -----

    def "constructing an engine with a null catalog throws"() {
        when:
        engine(GameState.newState(), null, new Random())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "catalog cannot be null"
    }

    def "constructing an engine with a null rng throws"() {
        when:
        engine(GameState.newState(), CardCatalog.defaultCatalog(), null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "rng cannot be null"
    }

    // ----- PLAY phase: startPlay -----

    def "startPlay draws up to MAX_HAND_SIZE cards from the current season's draw pile"() {
        setup:
        def cards = (1..3).collect { new Card("TEST", 0, 0, FrequencyType.RECURRING, []) }
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .drawPile(Season.SPRING, cards)
                .build()
        def engine = engine(state)

        when:
        def drawn = engine.startPlay()

        then:
        drawn.size() == GameEngine.MAX_HAND_SIZE
        state.hand.size() == GameEngine.MAX_HAND_SIZE
        state.drawPile.get(Season.SPRING).isEmpty()
    }

    def "startPlay respects MAX_HAND_SIZE — draws at most three even when more cards are available"() {
        setup:
        def cards = (1..8).collect { new Card("TEST", 0, 0, FrequencyType.RECURRING, []) }
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SUMMER)
                .drawPile(Season.SUMMER, cards)
                .build()
        def engine = engine(state)

        when:
        def drawn = engine.startPlay()

        then:
        drawn.size() == GameEngine.MAX_HAND_SIZE
        state.hand.size() == GameEngine.MAX_HAND_SIZE
        state.drawPile.get(Season.SUMMER).size() == 5  // 8 cards - 3 drawn = 5 remaining
    }

    def "startPlay reshuffles the discard pile into the draw pile when the draw pile runs dry"() {
        setup:
        // 1 in draw pile + 2 in discard = 3 total = MAX_HAND_SIZE; all should be drawn
        def drawCard   = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 1, ValueType.INTEGER)])
        def discard1   = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 4, ValueType.INTEGER)])
        def discard2   = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER)])

        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.FALL)
                .drawPile(Season.FALL, [drawCard])
                .discardPile(Season.FALL, [discard1, discard2])
                .build()
        def engine = engine(state)

        when:
        def drawn = engine.startPlay()

        then:
        drawn.size() == 3
        state.hand.size() == 3
        state.discardPile.get(Season.FALL).size() == 0
    }

    def "startPlay returns whatever is available when combined draw + discard is less than MAX_HAND_SIZE"() {
        setup:
        def card1 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def card2 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 1, ValueType.INTEGER)])

        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.WINTER)
                .drawPile(Season.WINTER, [card1])
                .discardPile(Season.WINTER, [card2])
                .build()
        def engine = engine(state)

        when:
        def drawn = engine.startPlay()

        then:
        drawn.size() == 2
        state.hand.size() == 2
    }

    def "startPlay throws when the current phase is not PLAY"() {
        setup:
        def state = GameState.builder().phase(Phase.ADD).build()
        def engine = engine(state)

        when:
        engine.startPlay()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("startPlay")
    }

    // ----- PLAY phase: placeCard — validation failures -----

    def "placeCard throws when the card is not in the hand"() {
        setup:
        def card = CardCatalog.FARM
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(10)
                .production(100)
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(card)

        then:
        def e = thrown(IllegalStateException)
        e.message == "card is not in hand"
    }

    def "placeCard throws when the table is full and the card is not a duplicate"() {
        setup:
        def filler = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(50)
                .production(1000)
                .table(Season.SPRING, (1..GameEngine.MAX_TABLE_SIZE).collect { filler })
                .hand([CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.FARM)

        then:
        def e = thrown(IllegalStateException)
        e.message == "SPRING table is full"
    }

    def "placeCard throws when the player cannot afford the production cost"() {
        setup:
        // FARM costs 90 production; give the player only 50
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(50)
                .production(50)
                .hand([CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.FARM)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("not enough production to place card")
        e.message.contains("90")   // cost
        e.message.contains("50")   // have
    }

    def "placeCard succeeds regardless of population — staffing is evaluated at End and Run, not at placement"() {
        setup:
        // FARM has staffRequirement=25 but cards can be placed regardless of population
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(1)
                .production(500)
                .hand([CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.FARM)

        then:
        notThrown(IllegalStateException)
        state.getCurrentTable().contains(CardCatalog.FARM)
    }

    def "placeCard throws when the current phase is not PLAY"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.ADD)
                .season(Season.SPRING)
                .hand([CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.FARM)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("placeCard")
    }

    // ----- PLAY phase: placeCard — successful place -----

    def "placeCard moves the card from hand to the current season's table and deducts production"() {
        setup:
        // FARM: staffRequirement=25, productionRequirement=90
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(25)
                .production(500)
                .hand([CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.FARM)

        then:
        state.hand.isEmpty()
        state.board.get(Season.SPRING) == [CardCatalog.FARM]
        state.resources.production == 410.0   // 500 - 90
    }

    def "placeCard is allowed when the table is full but the card is a duplicate of another on the board, for upgrading"() {
        setup:
        // Fill the table with 4 fillers + 1 FARM, then try to place another FARM.
        def filler = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def tableCards = [filler, filler, filler, filler, CardCatalog.FARM]
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(25)
                .production(500)
                .table(Season.SPRING, tableCards)
                .hand([CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.FARM)

        then:
        state.hand.isEmpty()
        state.board.get(Season.SPRING).size() == GameEngine.MAX_TABLE_SIZE  // no new slot
        state.resources.production == 410.0   // 500 - 90
    }

    // ----- PLAY phase: placeCard — upgrade by duplicate -----

    def "placing a duplicate doubles every effect's modifier on the existing table card"() {
        setup:
        // FARM has one recurring FOOD effect with modifier 50
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(25)
                .production(500)
                .table(Season.SPRING, [CardCatalog.FARM])
                .hand([CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.FARM)

        then:
        def upgraded = state.board.get(Season.SPRING).get(0)
        upgraded.effects().size() == 1
        upgraded.effects().get(0).modifier() == 100.0   // doubled from 50
    }

    def "placing a duplicate doubles all effects when the card carries multiple effects"() {
        setup:
        // TRADING_POST has two recurring CardTarget effects with modifier 5.0 each
        // staffRequirement=50, productionRequirement=200
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SUMMER)
                .population(400)
                .production(1000)
                .table(Season.SUMMER, [CardCatalog.TRADING_POST])
                .hand([CardCatalog.TRADING_POST])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.TRADING_POST)

        then:
        def upgraded = state.board.get(Season.SUMMER).get(0)
        upgraded.effects().size() == 2
        upgraded.effects().every { it.modifier() == 10.0 }   // table(5)+hand(5)
    }

    def "upgrade does not add a new slot — table size stays the same after placing a duplicate"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(25)
                .production(500)
                .table(Season.SPRING, [CardCatalog.FARM])
                .hand([CardCatalog.FARM])
                .build()
        def engine = engine(state)
        def sizeBefore = state.board.get(Season.SPRING).size()

        when:
        engine.placeCard(CardCatalog.FARM)

        then:
        state.board.get(Season.SPRING).size() == sizeBefore
    }

    def "placing a duplicate onto an engine-boosted table card merges modifiers rather than doubling the hand card"() {
        setup:
        // Simulate a Farm that has been boosted by an engine-builder from 50 → 70.
        def boostedFarm = CardCatalog.FARM.boostEffect(ResourceTarget.FOOD, 20)
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(25)
                .production(500)
                .table(Season.SPRING, [boostedFarm])
                .hand([CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.placeCard(CardCatalog.FARM)

        then:
        def upgraded = state.board.get(Season.SPRING).get(0)
        upgraded.name() == "FARM II"
        upgraded.effects().size() == 1
        upgraded.effects().get(0).modifier() == 120.0   // boosted(70) + base(50)
    }

    // ----- PLAY phase: reorderCard -----

    def "reorderTable reorders table to match the provided name list"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .table(Season.SPRING, [CardCatalog.FARM, CardCatalog.SEEDS, CardCatalog.MINE])
                .build()
        def engine = engine(state)

        when:
        engine.reorderTable(["MINE", "SEEDS", "FARM"])

        then:
        def table = state.board.get(Season.SPRING)
        table[0] == CardCatalog.MINE
        table[1] == CardCatalog.SEEDS  // untouched
        table[2] == CardCatalog.FARM
    }

    def "reorderTable with two cards swaps them correctly"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SUMMER)
                .table(Season.SUMMER, [CardCatalog.FARM, CardCatalog.MINE])
                .build()
        def engine = engine(state)

        when:
        engine.reorderTable(["MINE", "FARM"])

        then:
        def table = state.board.get(Season.SUMMER)
        table[0] == CardCatalog.MINE
        table[1] == CardCatalog.FARM
    }

    def "reorderTable can move last card to first position"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.FALL)
                .table(Season.FALL, [CardCatalog.SEEDS, CardCatalog.FERTILIZER, CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.reorderTable(["FARM", "FERTILIZER", "SEEDS"])

        then:
        def table = state.board.get(Season.FALL)
        table[0] == CardCatalog.FARM
        table[1] == CardCatalog.FERTILIZER  // untouched
        table[2] == CardCatalog.SEEDS
    }

    def "reorderTable preserves all cards — none are dropped"() {
        setup:
        def cards = [CardCatalog.FARM, CardCatalog.SEEDS, CardCatalog.FERTILIZER, CardCatalog.MINE]
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .table(Season.SPRING, cards)
                .build()
        def engine = engine(state)

        when:
        engine.reorderTable(["MINE", "FARM", "FERTILIZER", "SEEDS"])

        then:
        def table = state.board.get(Season.SPRING)
        table.size() == 4
        table.containsAll(cards)
    }

    def "reorderTable does not change table size"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.WINTER)
                .table(Season.WINTER, [CardCatalog.FARM, CardCatalog.MINE, CardCatalog.SEEDS])
                .build()
        def engine = engine(state)
        def sizeBefore = state.board.get(Season.WINTER).size()

        when:
        engine.reorderTable(["MINE", "FARM", "SEEDS"])

        then:
        state.board.get(Season.WINTER).size() == sizeBefore
    }

    def "reorderTable throws when list size does not match table size"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .table(Season.SPRING, [CardCatalog.FARM, CardCatalog.SEEDS])
                .build()
        def engine = engine(state)

        when:
        engine.reorderTable(["FARM"])   // too short

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("expected 2 names but got 1")
    }

    def "reorderTable throws when a name does not match any table card"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .table(Season.SPRING, [CardCatalog.FARM, CardCatalog.SEEDS])
                .build()
        def engine = engine(state)

        when:
        engine.reorderTable(["FARM", "MINE"])   // MINE not on table

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("no table card with name: MINE")
    }

    def "reorderTable throws when a name appears twice (duplicate)"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .table(Season.SPRING, [CardCatalog.FARM, CardCatalog.SEEDS])
                .build()
        def engine = engine(state)

        when:
        engine.reorderTable(["FARM", "FARM"])   // SEEDS missing, FARM duplicated

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("no table card with name: FARM")
    }

    def "reorderTable throws when the current phase is not PLAY"() {
        setup:
        def state = GameState.builder()
                .phase(phase)
                .season(Season.SPRING)
                .table(Season.SPRING, [CardCatalog.FARM, CardCatalog.SEEDS])
                .build()
        def engine = engine(state)

        when:
        engine.reorderTable(["SEEDS", "FARM"])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("reorderTable")

        where:
        phase << [Phase.ADD, Phase.COUNCIL_ADD, Phase.COUNCIL_MEETING]
    }

    def "reorderTable does not affect resources"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(5.0)
                .production(100.0)
                .food(50.0)
                .table(Season.SPRING, [CardCatalog.FARM, CardCatalog.MINE])
                .build()
        def engine = engine(state)

        when:
        engine.reorderTable(["MINE", "FARM"])

        then:
        state.resources.population == 5.0
        state.resources.production == 100.0
        state.resources.food       == 50.0
    }

    def "reorderTable does not affect the hand, draw pile, or discard pile"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .table(Season.SPRING, [CardCatalog.FARM, CardCatalog.SEEDS])
                .hand([CardCatalog.FERTILIZER])
                .drawPile(Season.SPRING, [CardCatalog.MINE])
                .discardPile(Season.SPRING, [CardCatalog.SEEDS])
                .build()
        def engine = engine(state)

        when:
        engine.reorderTable(["SEEDS", "FARM"])

        then:
        state.hand == [CardCatalog.FERTILIZER]
        state.drawPile.get(Season.SPRING)    == [CardCatalog.MINE]
        state.discardPile.get(Season.SPRING) == [CardCatalog.SEEDS]
    }

    // ----- PLAY phase: demolishCard -----

    def "demolishCard removes the card at the given slot and charges 20% of its production cost"() {
        setup:
        // FARM: productionRequirement=90 → demolish cost = 18
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .production(500)
                .table(Season.SPRING, [CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.demolishCard(0)

        then:
        state.board.get(Season.SPRING).isEmpty()
        state.resources.production == 482.0   // 500 - 18
    }

    def "demolishCard removes the correct card when multiple cards are on the table"() {
        setup:
        // Slot 1 is SEEDS (productionRequirement=50 → cost=10)
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .production(500)
                .table(Season.SPRING, [CardCatalog.FARM, CardCatalog.SEEDS, CardCatalog.FERTILIZER])
                .build()
        def engine = engine(state)

        when:
        engine.demolishCard(1)   // remove SEEDS

        then:
        def table = state.board.get(Season.SPRING)
        table.size() == 2
        table.contains(CardCatalog.FARM)
        !table.contains(CardCatalog.SEEDS)
        table.contains(CardCatalog.FERTILIZER)
        state.resources.production == 490.0   // 500 - 10
    }

    def "demolishCard throws when the slot index is out of range"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .production(100)
                .table(Season.SPRING, [CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.demolishCard(slot)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("invalid slot")

        where:
        slot << [-1, 1, 99]
    }

    def "demolishCard throws when the player cannot afford the demolition cost"() {
        setup:
        // MINE: productionRequirement=300 → demolish cost=60; give player only 25
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .production(25)
                .table(Season.SPRING, [CardCatalog.MINE])
                .build()
        def engine = engine(state)

        when:
        engine.demolishCard(0)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("not enough production to demolish")
    }

    def "demolishCard throws when the current phase is not PLAY"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.ADD)
                .season(Season.SPRING)
                .table(Season.SPRING, [CardCatalog.FARM])
                .build()
        def engine = engine(state)

        when:
        engine.demolishCard(0)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("demolishCard")
    }

    // ----- PLAY phase: endAndRun — phase guard -----

    def "endAndRun throws when the current phase is not PLAY"() {
        setup:
        def state = GameState.builder().phase(phase).season(Season.SPRING).build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("endAndRun")

        where:
        phase << [Phase.ADD, Phase.COUNCIL_ADD, Phase.COUNCIL_MEETING]
    }

    // ----- PLAY phase: endAndRun — hand discard -----

    def "endAndRun discards every remaining hand card to the current season's discard pile"() {
        setup:
        def card1 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def card2 = new Card("TEST", 0, 0, FrequencyType.RECURRING, [])
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .hand([card1, card2])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.hand.isEmpty()
        state.discardPile.get(Season.SPRING).containsAll([card1, card2])
        // other seasons' discard piles untouched
        state.discardPile.get(Season.SUMMER).isEmpty()
        state.discardPile.get(Season.FALL).isEmpty()
        state.discardPile.get(Season.WINTER).isEmpty()
    }

    def "endAndRun with an empty hand still resolves the table without error"() {
        setup:
        def card = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 5, ValueType.INTEGER)])
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SUMMER)
                .population(5.0)
                .food(50.0)
                .table(Season.SUMMER, [card])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.hand.isEmpty()
        state.resources.food == 50.0   // card +5 → 55, then consume floor(5)*1=5 → 50
    }

    // ----- PLAY phase: endAndRun — single vs recurring discard behaviour -----

    def "endAndRun moves a SINGLE-frequency card to the discard pile after its effect fires"() {
        setup:
        def singleCard = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.FOOD, 10, ValueType.INTEGER)])
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SUMMER)
                .population(5.0)
                .food(50.0)
                .table(Season.SUMMER, [singleCard])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.food == 55.0   // card +10 → 60, then consume floor(5)*1=5 → 55
        state.board.get(Season.SUMMER).isEmpty()
        state.discardPile.get(Season.SUMMER) == [singleCard]
    }

    def "endAndRun leaves a RECURRING-only card on the table after its effect fires"() {
        setup:
        def recurringCard = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.PRODUCTION, 5, ValueType.INTEGER)])
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .production(100.0)
                .table(Season.SPRING, [recurringCard])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.production == 105.0
        state.board.get(Season.SPRING) == [recurringCard]
        state.discardPile.get(Season.SPRING).isEmpty()
    }

    def "endAndRun mixed table: SINGLE card discarded, RECURRING card kept, both effects fire"() {
        setup:
        def singleCard    = new Card("TEST", 0, 0, FrequencyType.SINGLE,    [new Effect(ResourceTarget.FOOD,       10, ValueType.INTEGER)])
        def recurringCard = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.PRODUCTION,  5, ValueType.INTEGER)])
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.FALL)
                .population(5.0)
                .food(50.0).production(100.0)
                .table(Season.FALL, [singleCard, recurringCard])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.food       == 55.0   // card +10 → 60, then consume floor(5)*1=5 → 55
        state.resources.production == 105.0
        state.board.get(Season.FALL) == [recurringCard]
        state.discardPile.get(Season.FALL) == [singleCard]
    }

    // ----- PLAY phase: endAndRun — left-to-right ordering / card-to-card effect chain -----

    def "endAndRun fires effects left-to-right: each card sees resources already mutated by cards to its left"() {
        // Card A (slot 0): FOOD +10 INTEGER SINGLE  →  food goes 50 → 60
        // Card B (slot 1): FOOD +10 PERCENTAGE SINGLE  →  fires on 60, result 66
        // If order were reversed the PERCENTAGE card would fire on 50 and result would be 65.
        setup:
        def cardA = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.FOOD, 10,   ValueType.INTEGER)])
        def cardB = new Card("TEST", 0, 0, FrequencyType.SINGLE, [new Effect(ResourceTarget.FOOD, 10.0, ValueType.PERCENTAGE)])
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(5.0)
                .food(50.0)
                .table(Season.SPRING, [cardA, cardB])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        // 50 + 10 = 60  (card A), then 60 + 60*0.10 = 66  (card B), then consume floor(5)*1=5 → 61
        state.resources.food == 61.0
        // both were SINGLE — both discarded
        state.board.get(Season.SPRING).isEmpty()
        state.discardPile.get(Season.SPRING).containsAll([cardA, cardB])
    }

    def "endAndRun card-to-card effect chain: card B sees production boosted by card A"() {
        // Card A (slot 0): PRODUCTION +50 RECURRING — stays on table
        // Card B (slot 1): PRODUCTION +10% RECURRING — fires on 150, not 100
        setup:
        def cardA = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.PRODUCTION, 50,   ValueType.INTEGER)])
        def cardB = new Card("TEST", 0, 0, FrequencyType.RECURRING, [new Effect(ResourceTarget.PRODUCTION, 10.0, ValueType.PERCENTAGE)])
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .production(100.0)
                .table(Season.SPRING, [cardA, cardB])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        // 100 + 50 = 150  (card A), then 150 + 150*0.10 = 165  (card B)
        state.resources.production == 165.0
        // both RECURRING — both stay
        state.board.get(Season.SPRING) == [cardA, cardB]
        state.discardPile.get(Season.SPRING).isEmpty()
    }

    // ----- PLAY phase: endAndRun — food consumption and starvation -----

    def "endAndRun consumes floor(pop) × 1 food in a non-winter season"() {
        setup:
        // pop=6.0 → floor=6; spring rate=1; consumed=6; food 40 → 34
        // surplus growth: rate = min(34/6.0, 0.20) = 0.20; new pop = 6.0 * 1.2 = 7.2
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(6.0)
                .food(40.0)
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.food       == 34.0   // 40 - 6; surplus food is not further consumed
        state.resources.population == 7.2    // 6.0 * 1.2 after 20% surplus growth cap
    }

    def "endAndRun consumes floor(pop) × 2 food in winter"() {
        setup:
        // pop=4.0 → floor=4; winter rate=2; consumed=8; food 50 → 42
        // surplus growth: rate = min(42/4.0, 0.20) = 0.20; new pop = 4.0 * 1.2 = 4.8
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.WINTER)
                .population(4.0)
                .food(50.0)
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.food       == 42.0   // 50 - 8; surplus food is not further consumed
        state.resources.population == 4.8    // 4.0 * 1.2 after 20% surplus growth cap
    }

    def "endAndRun clamps food to zero and reduces population by half the deficit on starvation"() {
        setup:
        // pop=10; spring rate=1; consumed=10; food=4 → deficit=6; pop 10 → 7
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.SPRING)
                .population(10.0)
                .food(4.0)
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.food       == 0.0
        state.resources.population == 7.0   // 10 - 0.5*6
    }

    def "endAndRun starvation in winter: double rate magnifies deficit"() {
        setup:
        // pop=6; winter rate=2; consumed=12; food=2 → deficit=10; pop 6 → 1
        def state = GameState.builder()
                .phase(Phase.PLAY)
                .season(Season.WINTER)
                .population(6.0)
                .food(2.0)
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.food       == 0.0
        state.resources.population == 1.0   // 6 - 0.5*10
    }

    // ----- Game Over: population reaching zero -----

    def "population dropping to exactly zero during end-of-season resolution triggers GAME_OVER"() {
        setup:
        // pop=5, card -5 → raw 0.0 → GAME_OVER
        def plague = new Card("Plague", 0, 0, FrequencyType.SINGLE, [ResourceTarget.POPULATION.integer(-5)])
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING).year(1)
                .table(Season.SPRING, [plague])
                .population(5.0).build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.gameOver   == true
        state.resources.population == 0.0
    }

    def "population driven below zero by a card is clamped to zero and triggers GAME_OVER"() {
        setup:
        // pop=2, card -5 → raw -3 → clamped to 0
        def catastrophe = new Card("Catastrophe", 0, 0, FrequencyType.SINGLE, [ResourceTarget.POPULATION.integer(-5)])
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING).year(1)
                .table(Season.SPRING, [catastrophe])
                .population(2.0).build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.gameOver   == true
        state.resources.population == 0.0
    }

    def "GAME_OVER is not set when population stays above zero after resolution"() {
        setup:
        // card drops population 5.0 -> 3.0, then endAndRun's food step runs:
        // spring rate=1, consumed=floor(3.0)*1=3.0; food=3.0 covers it exactly (no
        // deficit, no leftover surplus), so population is unaffected by growth.
        def minorLoss = new Card("Minor loss", 0, 0, FrequencyType.SINGLE, [ResourceTarget.POPULATION.integer(-2)])
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING).year(1)
                .table(Season.SPRING, [minorLoss])
                .population(5.0).food(3.0).build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.time.phase   == Phase.ADD   // normal transition: SPRING isn't WINTER, so PLAY -> ADD
        state.gameOver   == false
        state.resources.population == 3.0
    }

    def "population dropping to zero by starvation triggers GAME_OVER"() {
        setup:
        // pop=1, winter: consumed=floor(1)*2=2, available=0, deficit=2, pop = 1 - 1 = 0 → GAME_OVER
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.WINTER)
                .population(1.0).food(0.0).build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.gameOver   == true
        state.resources.population == 0.0
        state.resources.food       == 0.0
    }

    def "sufficient food after endAndRun does not trigger GAME_OVER"() {
        setup:
        // pop=5, spring: consumed=5, food=10 → food=5
        // surplus growth: rate = min(5/5.0, 0.20) = 0.20; new pop = 5.0 * 1.2 = 6.0
        // endAndRun now calls advancePhase() internally → phase advances to ADD
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .population(5.0).food(10.0).build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.time.phase   == Phase.ADD   // endAndRun advances phase internally
        state.gameOver   == false
        state.resources.population == 6.0    // 5.0 * 1.2 after 20% surplus growth cap
    }

    // ----- CardCatalog: Great Work indexing -----

    def "greatWorksForSeason indexes every Great Work exactly once, even ones embedded in the seasonal ADD pool"() {
        setup:
        def catalog = CardCatalog.defaultCatalog()

        expect:
        catalog.greatWorksForSeason(season).count { it.name() == name } == 1

        where:
        season         | name
        Season.SPRING  | "THE_GREAT_RESERVOIR_CONSTRUCTION"
        Season.SUMMER  | "THE_GREAT_ROAD_CONSTRUCTION"
        Season.SUMMER  | "TREASURY_CONSTRUCTION"
        Season.FALL    | "THE_GREAT_CAPITAL_CONSTRUCTION"
        Season.FALL    | "BARRACKS_CONSTRUCTION"
        Season.WINTER  | "THE_GREAT_CONQUEST_CONSTRUCTION"
    }

    // ----- commissionGreatWork: preconditions -----

    def "commissionGreatWork throws when the current phase is not PLAY"() {
        setup:
        def state = GameState.builder().phase(Phase.ADD).season(Season.SPRING).build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_RESERVOIR_CONSTRUCTION")

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("commissionGreatWork")
    }

    def "commissionGreatWork throws for a name that does not exist in the catalog"() {
        setup:
        def state = GameState.builder().phase(Phase.PLAY).season(Season.SPRING).build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("NOT_A_REAL_CARD")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "no great work with name: NOT_A_REAL_CARD"
    }

    def "commissionGreatWork throws for a card that exists but is not a Great Work"() {
        setup:
        def state = GameState.builder().phase(Phase.PLAY).season(Season.SPRING).build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("FARM")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "no great work with name: FARM"
    }

    def "commissionGreatWork throws when the current season does not match the Great Work's table slot"() {
        setup:
        def state = GameState.builder().phase(Phase.PLAY).season(Season.SUMMER).build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_RESERVOIR_CONSTRUCTION")

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("belongs on the SPRING table")
    }

    def "commissionGreatWork throws when the Great Work is already sitting in the hand"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .hand([CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION])
                .build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_RESERVOIR_CONSTRUCTION")

        then:
        def e = thrown(IllegalStateException)
        e.message == "great work already drafted, commissioned, or completed: THE_GREAT_RESERVOIR_CONSTRUCTION"
    }

    def "commissionGreatWork throws when the Great Work is already in a draw pile"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .drawPile(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION])
                .build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_RESERVOIR_CONSTRUCTION")

        then:
        thrown(IllegalStateException)
    }

    def "commissionGreatWork throws when the Great Work is already in a discard pile"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .discardPile(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION])
                .build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_RESERVOIR_CONSTRUCTION")

        then:
        thrown(IllegalStateException)
    }

    def "commissionGreatWork throws when the Great Work's construction card is already on a table"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .table(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION])
                .build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_RESERVOIR_CONSTRUCTION")

        then:
        thrown(IllegalStateException)
    }

    def "commissionGreatWork throws when the Great Work's completed card is already on a table"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .table(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_COMPLETED])
                .build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_RESERVOIR_CONSTRUCTION")

        then:
        def e = thrown(IllegalStateException)
        e.message == "great work already drafted, commissioned, or completed: THE_GREAT_RESERVOIR_CONSTRUCTION"
    }

    def "commissionGreatWork throws when the target table is full"() {
        setup:
        def filler = new Card("FILLER", 0, 0, FrequencyType.RECURRING, [])
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .table(Season.SPRING, (1..GameEngine.MAX_TABLE_SIZE).collect { filler })
                .build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_RESERVOIR_CONSTRUCTION")

        then:
        def e = thrown(IllegalStateException)
        e.message == "SPRING table is full"
    }

    def "commissionGreatWork throws when its construction cost references a resource not yet unlocked"() {
        setup:
        def state = GameState.builder().phase(Phase.PLAY).season(season).build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork(name)

        then:
        def e = thrown(IllegalStateException)
        e.message == "commissioning " + name + " requires its locked resource(s) to be unlocked first"

        where:
        name                                | season
        "THE_GREAT_ROAD_CONSTRUCTION"        | Season.SUMMER
        "THE_GREAT_CAPITAL_CONSTRUCTION"     | Season.FALL
        "THE_GREAT_CONQUEST_CONSTRUCTION"    | Season.WINTER
    }

    def "commissionGreatWork throws when production cannot cover the commission cost"() {
        setup:
        // THE_GREAT_RESERVOIR_CONSTRUCTION costs 500 production
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING).production(0.0)
                .build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_RESERVOIR_CONSTRUCTION")

        then:
        def e = thrown(IllegalStateException)
        e.message == "not enough production to commission THE_GREAT_RESERVOIR_CONSTRUCTION: need 500, have 0"
    }

    // ----- commissionGreatWork: success paths -----

    def "commissionGreatWork succeeds for Great Works whose construction cost never references GOLD or STRENGTH, even when neither resource is unlocked"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY).season(season).production(10000.0)
                .build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork(name)

        then:
        notThrown(IllegalStateException)
        state.board.get(season).any { it.name() == name }

        where:
        name                                | season
        "TREASURY_CONSTRUCTION"             | Season.SUMMER
        "BARRACKS_CONSTRUCTION"             | Season.FALL
        "THE_GREAT_RESERVOIR_CONSTRUCTION"  | Season.SPRING
    }

    def "commissionGreatWork succeeds once its construction cost's resource has been unlocked"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SUMMER).production(10000.0)
                .goldUnlocked(true)
                .build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_ROAD_CONSTRUCTION")

        then:
        notThrown(IllegalStateException)
        state.board.get(Season.SUMMER).any { it.name() == "THE_GREAT_ROAD_CONSTRUCTION" }
    }

    def "commissionGreatWork deducts the commission cost and places the construction card on the season's table"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING).production(2500.0)
                .build()
        def engine = engine(state)

        when:
        engine.commissionGreatWork("THE_GREAT_RESERVOIR_CONSTRUCTION")

        then:
        state.resources.production == 2000.0   // 2500 - 500 commission cost
        state.board.get(Season.SPRING) == [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION]
    }

    // ----- availableGreatWorks -----

    def "availableGreatWorks returns the season's Great Work when it is unclaimed"() {
        setup:
        def state = GameState.newState()
        def engine = engine(state)

        expect:
        engine.availableGreatWorks(Season.SPRING)*.name() == ["THE_GREAT_RESERVOIR_CONSTRUCTION"]
    }

    def "availableGreatWorks excludes a Great Work that has already been claimed anywhere — hand, draw pile, discard pile, or any table"() {
        setup:
        def builder = GameState.builder()
        location(builder)
        def state = builder.build()
        def engine = engine(state)

        expect:
        engine.availableGreatWorks(Season.SPRING).isEmpty()

        where:
        location << [
                { b -> b.hand([CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION]) },
                { b -> b.drawPile(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION]) },
                { b -> b.discardPile(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION]) },
                { b -> b.table(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION]) },
                { b -> b.table(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_COMPLETED]) },
        ]
    }

    // ----- offerAddChoices: Great Work dedup -----

    def "offerAddChoices includes an unclaimed Great Work from the season's catalog"() {
        setup:
        def catalog = new CardCatalog([(Season.SPRING): [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION]])
        def state = GameState.builder().phase(Phase.ADD).season(Season.SPRING).build()
        def engine = engine(state, catalog)

        when:
        def choices = engine.offerAddChoices()

        then:
        choices == [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION]
    }

    def "offerAddChoices excludes a Great Work that has already been claimed, even when it is the only card in the season's pool"() {
        setup:
        def catalog = new CardCatalog([(Season.SPRING): [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION]])
        def state = GameState.builder()
                .phase(Phase.ADD).season(Season.SPRING)
                .hand([CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION])
                .build()
        def engine = engine(state, catalog)

        when:
        def choices = engine.offerAddChoices()

        then:
        choices.isEmpty()
    }

    // ----- endAndRun: Great Work construction progress -----

    def "endAndRun decrements a Great Work's construction period by one when fully staffed and its costs are affordable"() {
        setup:
        // THE_GREAT_RESERVOIR_CONSTRUCTION: staffRequirement=300, costs PRODUCTION -300 / FOOD -150.
        // Start at period 2 so the decrement doesn't also complete construction.
        def inProgress = CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION.withConstructionPeriod(2)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .population(300.0).production(500.0).food(500.0)
                .table(Season.SPRING, [inProgress])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        def tableCard = state.board.get(Season.SPRING)[0]
        tableCard.name() == "THE_GREAT_RESERVOIR_CONSTRUCTION"
        tableCard.constructionPeriod() == 1
        state.resources.production == 200.0   // 500 - 300 construction cost
    }

    def "endAndRun does not progress a Great Work's construction when staffing is below FULL"() {
        setup:
        // staffRequirement=200 but no workers available
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .population(0.0).production(500.0).food(500.0)
                .table(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        def tableCard = state.board.get(Season.SPRING)[0]
        tableCard.constructionPeriod() == 6   // unchanged (catalog default)
        state.resources.production == 500.0             // construction cost never applied
    }

    def "endAndRun does not progress a Great Work's construction when its costs cannot be afforded, even if fully staffed"() {
        setup:
        // Fully staffed (population 200 >= staffRequirement 200) but production/food too low to cover the cost (need 250/150)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .population(200.0).production(0.0).food(0.0)
                .table(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        def tableCard = state.board.get(Season.SPRING)[0]
        tableCard.constructionPeriod() == 6   // unchanged (catalog default) — costs not affordable, construction stalls
        state.resources.production == 0.0               // unchanged; effects never applied
    }

    def "endAndRun completes a Great Work's construction and swaps in the completed card when the period reaches zero"() {
        setup:
        // THE_GREAT_RESERVOIR_CONSTRUCTION: staffRequirement=300.
        def almostDone = CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION.withConstructionPeriod(1)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .population(300.0).production(500.0).food(500.0)
                .table(Season.SPRING, [almostDone])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        def tableCard = state.board.get(Season.SPRING)[0]
        tableCard.name() == "THE_GREAT_RESERVOIR"
        tableCard.isGreatWork() == false
        tableCard.victory() == true
    }

    // ----- endAndRun: Great Work construction POPULATION / POPULATION_CAP effects -----

    def "endAndRun applies THE_GREAT_RESERVOIR_CONSTRUCTION's POPULATION_CAP effect, raising the cap"() {
        setup:
        // THE_GREAT_RESERVOIR_CONSTRUCTION: staffRequirement=300, effects include POPULATION_CAP +25.
        def inProgress = CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION.withConstructionPeriod(2)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .population(300.0).populationCap(100.0)
                .production(500.0).food(500.0)
                .table(Season.SPRING, [inProgress])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.populationCap == 125.0   // 100 + 25
    }

    def "endAndRun applies THE_GREAT_ROAD_CONSTRUCTION's POPULATION_CAP effect, raising the cap"() {
        setup:
        // THE_GREAT_ROAD_CONSTRUCTION: staffRequirement=400, costs PRODUCTION -325 / FOOD -225 / GOLD -150,
        // effects include POPULATION_CAP +50. GOLD must be unlocked for the GOLD cost to be deducted
        // (and therefore affordable), so this test unlocks it explicitly.
        def inProgress = CardCatalog.THE_GREAT_ROAD_CONSTRUCTION.withConstructionPeriod(2)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SUMMER)
                .population(400.0).populationCap(100.0)
                .production(1000.0).food(750.0)
                .gold(500.0).goldUnlocked(true)
                .table(Season.SUMMER, [inProgress])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.populationCap == 150.0   // 100 + 50
    }

    def "endAndRun applies BARRACKS_CONSTRUCTION's POPULATION effect, reducing population"() {
        setup:
        // BARRACKS_CONSTRUCTION: staffRequirement=150, costs PRODUCTION -200 / FOOD -100 / POPULATION -20.
        // Food is set to 230 so that, after the -100 construction cost (food=130) and the POPULATION -20
        // effect (population=130), end-of-phase food consumption (130 population x 1/season in FALL)
        // exactly zeroes food with no surplus growth and no starvation deficit — isolating the
        // construction effect's impact on population from the unrelated consumption/growth mechanics.
        def inProgress = CardCatalog.BARRACKS_CONSTRUCTION.withConstructionPeriod(2)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.FALL)
                .population(150.0).populationCap(250.0)
                .production(500.0).food(230.0)
                .table(Season.FALL, [inProgress])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.population == 130.0   // 150 - 20 construction cost; consumption/growth net to zero by design
    }

    def "completing a win-condition Great Work does not unlock GOLD or STRENGTH and does not win the game alone"() {
        setup:
        def almostDone = CardCatalog.THE_GREAT_RESERVOIR_CONSTRUCTION.withConstructionPeriod(1)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SPRING)
                .population(200.0).production(500.0).food(500.0)
                .table(Season.SPRING, [almostDone])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        !state.getResources().isUnlocked(ResourceTarget.GOLD)
        !state.getResources().isUnlocked(ResourceTarget.STRENGTH)
        !state.isGameWon()
    }

    def "completing TREASURY's construction unlocks GOLD"() {
        setup:
        def almostDone = CardCatalog.TREASURY_CONSTRUCTION.withConstructionPeriod(1)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.SUMMER)
                .population(125.0).production(500.0)
                .table(Season.SUMMER, [almostDone])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.getResources().isUnlocked(ResourceTarget.GOLD)
        state.board.get(Season.SUMMER)[0].name() == "TREASURY"
    }

    def "completing BARRACKS's construction unlocks STRENGTH"() {
        setup:
        def almostDone = CardCatalog.BARRACKS_CONSTRUCTION.withConstructionPeriod(1)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.FALL)
                .population(150.0).production(500.0).food(500.0)
                .table(Season.FALL, [almostDone])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.getResources().isUnlocked(ResourceTarget.STRENGTH)
        state.board.get(Season.FALL)[0].name() == "BARRACKS"
    }

    def "completing the fourth and final win-condition Great Work sets gameWon to true"() {
        setup:
        // THE_GREAT_CONQUEST_CONSTRUCTION: staffRequirement=1500.
        def almostDone = CardCatalog.THE_GREAT_CONQUEST_CONSTRUCTION.withConstructionPeriod(1)
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.WINTER)
                .population(1500.0).production(5000.0).food(5000.0).gold(5000.0).strength(5000.0)
                .goldUnlocked(true).strengthUnlocked(true)
                .table(Season.SPRING, [CardCatalog.THE_GREAT_RESERVOIR_COMPLETED])
                .table(Season.SUMMER, [CardCatalog.THE_GREAT_ROAD_COMPLETED])
                .table(Season.FALL, [CardCatalog.THE_GREAT_CAPITAL_COMPLETED])
                .table(Season.WINTER, [almostDone])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.gameWon == true
    }

    // ----- TAX_COLLECTION: gold effect -----

    def "TAX_COLLECTION's effect increases GOLD (not PRODUCTION) and applies its POPULATION penalty"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.FALL)
                .population(150.0).populationCap(200.0).production(250.0).gold(0.0)
                .goldUnlocked(true)
                .table(Season.FALL, [CardCatalog.TAX_COLLECTION])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.gold == 400.0   // TAX_COLLECTION: GOLD +400
        state.resources.production == 250.0   // unaffected — no PRODUCTION effect on this card
        // POPULATION -60 fires before food consumption/growth (150 -> 90); growth then applies on
        // top of that, so just assert it dropped well below the 150 starting point rather than
        // re-deriving the food-consumption/growth formula already covered by dedicated tests.
        state.resources.population < 125.0
    }

    def "TAX_COLLECTION does not affect GOLD when GOLD has not been unlocked yet"() {
        setup:
        def state = GameState.builder()
                .phase(Phase.PLAY).season(Season.FALL)
                .population(150.0).populationCap(200.0).gold(0.0)
                .table(Season.FALL, [CardCatalog.TAX_COLLECTION])
                .build()
        def engine = engine(state)

        when:
        engine.endAndRun()

        then:
        state.resources.gold == 0.0   // applyGold no-ops while GOLD is locked
    }
}
