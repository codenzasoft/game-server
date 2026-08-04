package com.codenzasoft.games.kingshand.strategies

import com.codenzasoft.games.kingshand.CardCatalog
import com.codenzasoft.games.kingshand.GameEngine
import com.codenzasoft.games.kingshand.GameState
import com.codenzasoft.games.kingshand.Phase
import com.codenzasoft.games.kingshand.PlayerState
import com.codenzasoft.games.kingshand.Season
import com.codenzasoft.games.kingshand.TimeState
import spock.lang.Specification

/**
 * The search must never touch the live game. These specs pin the two guarantees that make that
 * true: {@link KhGameStateCloner} produces a genuinely independent copy, and {@link KhSimulator}
 * resolves a whole season on a clone while leaving the original state byte-for-byte unchanged.
 */
class KhGameStateClonerSpec extends Specification {

    private static GameState playState(List placements, List hand, double production) {
        def player = PlayerState.builder()
                .population(100).food(750).production(production)
                .table(Season.SPRING, placements)
                .hand(hand)
                .build()
        GameState.multiPlayer([player], new TimeState(Season.SPRING, Phase.PLAY, 1))
    }

    def "deep copy is independent — mutating the clone never touches the source"() {
        setup:
        def source = playState([CardCatalog.FARM], [CardCatalog.MINE], 1000.0)
        def clone = KhGameStateCloner.deepCopy(source)

        when: "the clone is mutated through a throwaway engine"
        def engine = new GameEngine(clone, new Random(1))
        engine.setActiveSeat(0)
        engine.placeCard(CardCatalog.MINE)

        then: "the source's hand, table, and production are all untouched"
        source.getPlayer(0).getHand().size() == 1
        source.getPlayer(0).getTable(Season.SPRING).collect { it.name() } == ["FARM"]
        source.getPlayer(0).getResources().production() == 1000.0

        and: "the clone actually changed"
        clone.getPlayer(0).getHand().isEmpty()
        clone.getPlayer(0).getTable(Season.SPRING).collect { it.name() }.contains("MINE")
        clone.getPlayer(0).getResources().production() < 1000.0
    }

    def "simulatePlay resolves a season on a copy without mutating the live state"() {
        setup:
        def real = playState([CardCatalog.FARM], [CardCatalog.MINE, CardCatalog.SEEDS], 1000.0)
        def beforeFood = real.getPlayer(0).getResources().food()
        def beforeProduction = real.getPlayer(0).getResources().production()
        def beforePopulation = real.getPlayer(0).getResources().population()
        def beforeHand = real.getPlayer(0).getHand().collect { it.name() }
        def beforeTable = real.getPlayer(0).getTable(Season.SPRING).collect { it.name() }

        when: "a full play plan is simulated (place + reorder + resolve)"
        def resolved = KhSimulator.simulatePlay(
                real, 0, [CardCatalog.MINE, CardCatalog.SEEDS], null,
                { table -> table.collect { it.name() } }, new Random(7))

        then: "the returned copy has advanced (food was consumed during resolution)"
        resolved.getPlayer(0).getResources() != real.getPlayer(0).getResources()

        and: "the live state is exactly as it was before the simulation"
        real.getPlayer(0).getResources().food() == beforeFood
        real.getPlayer(0).getResources().production() == beforeProduction
        real.getPlayer(0).getResources().population() == beforePopulation
        real.getPlayer(0).getHand().collect { it.name() } == beforeHand
        real.getPlayer(0).getTable(Season.SPRING).collect { it.name() } == beforeTable
    }
}
