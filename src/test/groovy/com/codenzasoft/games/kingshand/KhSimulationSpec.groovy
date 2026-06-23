package com.codenzasoft.games.kingshand

import com.codenzasoft.games.common.Player
import spock.lang.Specification

import java.util.random.RandomGenerator

/**
 * Monte Carlo simulation harness for King's Hand balance analysis.
 *
 * Runs many games with several simple bot strategies and prints a report covering:
 *   - Win rate per strategy
 *   - Year distribution at game end (median/mean for wins vs losses)
 *   - Per-card win-rate correlation (which cards appear more in winning games)
 *
 * This is a reporting tool, not a correctness test. The single feature method
 * always passes — run it directly to read the output:
 *
 *   ./gradlew test --tests "*.KhSimulationSpec" --info 2>&1 | grep -A 200 "SIMULATION REPORT"
 */
class KhSimulationSpec extends Specification {

    static final int NUM_GAMES   = 500   // games per strategy
    static final int MAX_SEASONS = 240   // safety cap (~60 years) to prevent infinite loops

    // ── Per-game result ────────────────────────────────────────────────────────

    static class SimResult {
        final String strategy
        final boolean won
        final int year
        final Season endSeason
        final double finalPop
        final double finalFood
        final double finalProd
        final boolean hitSafetyCap
        final List<String> addedCards

        SimResult(String strategy, GameState state, List<String> addedCards, boolean hitSafetyCap) {
            this.strategy     = strategy
            this.won          = state.gameWon
            this.year         = state.year
            this.endSeason    = state.curSeason
            this.finalPop     = state.population
            this.finalFood    = state.food
            this.finalProd    = state.production
            this.hitSafetyCap = hitSafetyCap
            this.addedCards   = List.copyOf(addedCards)
        }
    }

    // ── Strategy definition ────────────────────────────────────────────────────
    //
    // Each strategy is a map with three closures:
    //   pickCard(GameState, List<Card>) → Card      (ADD phase)
    //   pickEvent(GameState, Event)     → EventOption (RANDOM_EVENT phase)
    //   pickPlay(GameState, List<Card>) → List<Card>  (PLAY phase: cards to place, in order)

    static Map randomStrategy(Random rng) {[
        pickCard:  { GameState s, List<Card> offers -> offers[rng.nextInt(offers.size())] },
        pickEvent: { GameState s, Event evt -> evt.options()[rng.nextInt(evt.options().size())] },
        pickPlay:  { GameState s, List<Card> hand -> affordableSortedBy(s, hand) { Card c -> 0.0 } },
    ]}

    static Map foodFirstStrategy() {[
        pickCard:  { GameState s, List<Card> offers -> offers.max { c -> cardScore(c, 2.0, 0.2, 0.5, 0.1) } },
        pickEvent: { GameState s, Event evt         -> evt.options().max { o -> eventScore(o, 2.0, 0.1, 10.0, -15.0) } },
        pickPlay:  { GameState s, List<Card> hand   -> affordableSortedBy(s, hand) { c -> cardScore(c, 2.0, 0.2, 0.5, 0.1) } },
    ]}

    static Map productionFirstStrategy() {[
        pickCard:  { GameState s, List<Card> offers -> offers.max { c -> cardScore(c, 0.2, 2.0, 0.3, 0.3) } },
        pickEvent: { GameState s, Event evt         -> evt.options().max { o -> eventScore(o, 0.1, 2.0, 10.0, -15.0) } },
        pickPlay:  { GameState s, List<Card> hand   -> affordableSortedBy(s, hand) { c -> cardScore(c, 0.2, 2.0, 0.3, 0.3) } },
    ]}

    static Map balancedStrategy() {[
        pickCard:  { GameState s, List<Card> offers ->
            offers.max { c -> cardScore(c, foodWeight(s), prodWeight(s), 0.5, 0.2) }
        },
        pickEvent: { GameState s, Event evt ->
            evt.options().max { o -> eventScore(o, foodWeight(s), prodWeight(s), 10.0, -15.0) }
        },
        pickPlay:  { GameState s, List<Card> hand ->
            affordableSortedBy(s, hand) { c -> cardScore(c, foodWeight(s), prodWeight(s), 0.5, 0.2) }
        },
    ]}

    // ── Scoring helpers ────────────────────────────────────────────────────────

    /**
     * Score a card's desirability.
     *
     * @param foodW     weight for FOOD effects
     * @param prodW     weight for PRODUCTION effects
     * @param popW      weight for POPULATION effects (positive only)
     * @param cardW     weight for CardTarget (engine-builder) effects
     */
    static double cardScore(Card card, double foodW, double prodW, double popW, double cardW) {
        card.effects().stream().mapToDouble { Effect e ->
            switch (e.target()) {
                case ResourceTarget.FOOD           -> e.modifier() * foodW
                case ResourceTarget.PRODUCTION     -> e.modifier() * prodW
                case ResourceTarget.POPULATION     -> e.modifier() > 0 ? e.modifier() * popW : e.modifier() * 2.0
                case ResourceTarget.POPULATION_CAP -> e.modifier() > 0 ? e.modifier() * 0.3 : 0.0
                default                            -> e.modifier() * cardW  // CardTarget engine-builder
            }
        }.sum()
    }

    /**
     * Score an event option's desirability.
     *
     * @param popLossPenalty  absolute penalty per 1 population lost (negative modifier × this)
     */
    static double eventScore(EventOption opt, double foodW, double prodW, double popGainW, double popLossPenalty) {
        opt.effects().stream().mapToDouble { Effect e ->
            switch (e.target()) {
                case ResourceTarget.FOOD       -> e.modifier() * foodW
                case ResourceTarget.PRODUCTION -> e.modifier() * prodW
                case ResourceTarget.POPULATION -> e.modifier() > 0
                    ? e.modifier() * popGainW
                    : e.modifier() * popLossPenalty   // popLossPenalty is negative, so loss*penalty < 0
                default                        -> 0.0
            }
        }.sum()
    }

    /** Dynamic food weight: higher when stores are low relative to population. */
    static double foodWeight(GameState s) {
        double seasonsOfFood = s.population > 0 ? s.food / (s.population * 2) : 99.0
        Math.max(0.5, Math.min(3.0, 3.0 - seasonsOfFood))
    }

    /** Dynamic production weight: higher when production is scarce. */
    static double prodWeight(GameState s) {
        s.production < 400 ? 2.0 : s.production < 800 ? 1.2 : 0.6
    }

    /**
     * Returns the subset of {@code hand} that the bot wants to place, sorted by descending score,
     * filtering out cards the player cannot currently afford or that have no table slot.
     * Affordability is checked against the state at call time (before any placement); the placement
     * loop re-checks affordability card-by-card since each placement deducts production.
     */
    static List<Card> affordableSortedBy(GameState s, List<Card> hand, Closure<Double> scorer) {
        hand.sort(Comparator.comparingDouble { c -> -scorer(c) } as Comparator<Card>)
        hand.findAll { Card c ->
            s.production >= c.productionRequirement() &&
            (s.tableContains(c) || s.getCurrentTable().size() < GameEngine.MAX_TABLE_SIZE)
        }
    }

    // ── Game runner ────────────────────────────────────────────────────────────

    SimResult runGame(String strategyName, Map strategy, long seed) {
        def rng   = new Random(seed)
        def state = GameState.newState()
        def engine = new GameEngine.Builder()
            .withId(UUID.randomUUID())
            .withPlayer(new Player(UUID.randomUUID(), "bot"))
            .withState(state)
            .withRng(rng)
            .build()

        engine.initialize()

        List<String> addedCards = []
        int seasonCount = 0

        while (!state.gameOver && !state.gameWon && seasonCount < MAX_SEASONS) {
            switch (state.curPhase) {
                case Phase.ADD:
                    List<Card> offers = state.addChoices
                    if (offers.isEmpty()) offers = engine.offerAddChoices()
                    Card pick = strategy.pickCard(state, offers) as Card
                    addedCards << pick.name()
                    engine.pickCard(pick)
                    break

                case Phase.RANDOM_EVENT:
                    Event evt = engine.currentEvent()
                    if (evt != null) {
                        // Try the preferred option first; fall back through the rest if
                        // the engine rejects it (e.g. production cost would go negative).
                        EventOption preferred = strategy.pickEvent(state, evt) as EventOption
                        List<EventOption> fallbacks = [preferred] + evt.options().findAll { it != preferred }
                        boolean applied = false
                        for (EventOption opt : fallbacks) {
                            try { engine.applyEventOption(opt); applied = true; break }
                            catch (IllegalStateException ignored) {}
                        }
                        if (!applied) engine.advancePhase()
                    } else {
                        engine.advancePhase()
                    }
                    break

                case Phase.PLAY:
                    // Attempt to place each prioritised card; re-validate affordability
                    // after each placement since production is deducted on placement.
                    List<Card> toPlace = strategy.pickPlay(state, new ArrayList<>(state.hand)) as List<Card>
                    for (Card card : toPlace) {
                        if (!state.hand.contains(card)) continue
                        if (state.production < card.productionRequirement()) continue
                        if (!state.tableContains(card) && state.getCurrentTable().size() >= GameEngine.MAX_TABLE_SIZE) continue
                        try { engine.placeCard(card) } catch (ignored) {}
                        if (state.gameOver) break
                    }
                    engine.endAndRun()
                    seasonCount++
                    break
            }
        }

        return new SimResult(strategyName, state, addedCards, seasonCount >= MAX_SEASONS)
    }

    // ── Report formatting ──────────────────────────────────────────────────────

    static void printReport(String strategyName, List<SimResult> results) {
        def wins      = results.count { it.won }
        def losses    = results.size() - wins
        def hitCap    = results.count { it.hitSafetyCap }
        def winRate   = wins * 100.0 / results.size()

        def lossYears = results.findAll { !it.won && !it.hitSafetyCap }.collect { it.year }
        def winYears  = results.findAll {  it.won }.collect { it.year }
        def allYears  = results.collect { it.year }

        println ""
        println "═══════════════════════════════════════════════════════════════"
        println "  ${strategyName.padRight(18)} │  ${results.size()} games"
        println "═══════════════════════════════════════════════════════════════"
        println "  Win rate    : ${String.format('%5.1f', winRate)}%  (${wins} wins, ${losses} losses${hitCap > 0 ? ", ${hitCap} hit safety cap" : ''})"
        println "  Survival    : median ${median(allYears)} yr,  mean ${String.format('%.1f', mean(allYears))} yr"
        if (!lossYears.isEmpty()) {
            println "  Loss year   : median ${median(lossYears)},  mean ${String.format('%.1f', mean(lossYears))}"
        }
        if (!winYears.isEmpty()) {
            println "  Win year    : median ${median(winYears)},  mean ${String.format('%.1f', mean(winYears))}"
        }

        // Per-card survival-year correlation: does having this card in your deck make you survive longer?
        // Shows delta vs. baseline (games NOT containing the card).
        def baselineMeanYear = mean(allYears)
        def allCardNames = results.collectMany { it.addedCards }.unique().sort()
        def cardStats = allCardNames.collectEntries { String cardName ->
            def gamesWithCard    = results.findAll { cardName in it.addedCards }
            def gamesWithoutCard = results.findAll { !(cardName in it.addedCards) }
            def withMean    = mean(gamesWithCard.collect { it.year })
            def withoutMean = gamesWithoutCard.isEmpty() ? baselineMeanYear
                            : mean(gamesWithoutCard.collect { it.year })
            def delta = withMean - withoutMean
            [cardName, [n: gamesWithCard.size(), withMean: withMean, delta: delta,
                        wins: gamesWithCard.count { it.won }]]
        }.findAll { k, v -> (v.n as int) >= 15 }
         .sort     { a, b -> b.value.delta <=> a.value.delta }

        if (!cardStats.isEmpty()) {
            println ""
            println "  Card survival-year correlation (≥ 15 games, sorted best → worst delta):"
            println "  ─────────────────────────────────────────────────────────────"
            cardStats.each { String name, stats ->
                String delta  = String.format('%+.1f', stats.delta)
                String mean_  = String.format('%.1f', stats.withMean)
                String wins_  = wins > 0 ? " ${stats.wins}W" : ''
                println "  ${name.padRight(26)} ${delta.padLeft(6)} yr  (avg ${mean_} yr, n=${stats.n}${wins_})"
            }
        }
        println ""
    }

    static double mean(List<Integer> values) {
        values.isEmpty() ? 0.0 : values.sum() / values.size()
    }

    static int median(List<Integer> values) {
        def sorted = values.sort(false)
        sorted[sorted.size().intdiv(2)]
    }

    // ── The simulation feature ─────────────────────────────────────────────────

    def "simulate games and report balance metrics"() {
        given: "four strategies, each run for ${NUM_GAMES} games"
        def strategies = [
            [name: "Random",          factory: { long seed -> randomStrategy(new Random(seed)) }],
            [name: "Food-first",      factory: { long seed -> foodFirstStrategy() }],
            [name: "Production-first",factory: { long seed -> productionFirstStrategy() }],
            [name: "Balanced",        factory: { long seed -> balancedStrategy() }],
        ]

        when:
        println ""
        println "╔═══════════════════════════════════════════════════════════════╗"
        println "║              KING'S HAND — SIMULATION REPORT                  ║"
        println "╚═══════════════════════════════════════════════════════════════╝"
        println "  ${NUM_GAMES} games × ${strategies.size()} strategies = ${NUM_GAMES * strategies.size()} total runs"

        strategies.each { s ->
            List<SimResult> results = (0..<NUM_GAMES).collect { int i ->
                runGame(s.name as String, s.factory(i as long) as Map, i as long)
            }
            printReport(s.name as String, results)
        }

        then: "simulation completed without errors"
        true
    }
}
