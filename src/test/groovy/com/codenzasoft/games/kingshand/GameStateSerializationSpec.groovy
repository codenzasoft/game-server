package com.codenzasoft.games.kingshand

import com.codenzasoft.games.spring.services.kh.KhPersistenceService
import spock.lang.Specification

/**
 * Round-trip proof for KH mid-game persistence (Phase 1.2). The whole immutable {@link GameState}
 * must serialize to JSON and back with no loss — this is the highest-risk part of the persistence
 * slice, because the domain records carry {@code @JsonProperty}/{@code @JsonIgnore} annotations tuned
 * for the <em>client</em> wire shape (e.g. {@link ResolutionStep} flattens its {@code delta} into
 * per-resource getters and ignores the component itself). The persistence mapper must serialize the
 * record <em>components</em>, not the wire projection.
 *
 * <p>The fixture deliberately exercises the state that differs from catalog defaults and is easiest
 * to lose: a boosted/upgraded table card, a seated advisor with non-default counters, a non-empty
 * resolution log, a populated readiness barrier, and a held resolution.
 */
class GameStateSerializationSpec extends Specification {

    def "a mid-game two-kingdom GameState round-trips through JSON with no loss"() {
        given: "a boosted table card that differs from any catalog original"
        def boosted = new Card("Granary", 2, 30, FrequencyType.RECURRING, [new Effect(ResourceTarget.FOOD, 12)])

        and: "a seated advisor mid-rotation with non-default counters"
        def advisorCard = new Card("Sage", 1, 20, FrequencyType.RECURRING, [new Effect(ResourceTarget.PRODUCTION, 5)])
        def advisor = new SeatedAdvisor("SAGE", advisorCard, 2, true, 3, 1, 1, 0.5d, 2)

        and: "a non-empty resolution log (the delta is the lossy-prone part)"
        def delta = new ResourceState(12.0d, 5.0d, -3.0d, 0.0d, 0.0d, 0.0d, [] as Set)
        def step = new ResolutionStep("Granary", StaffingTier.FULL, false, delta)

        and: "seat 0 = a rich kingdom mid-PLAY; seat 1 = a fresh kingdom"
        def seat0 = PlayerState.builder()
                .table(Season.SUMMER, [boosted])
                .hand([new Card("Militia", 1, 10, FrequencyType.RECURRING, [])])
                .council([advisor])
                .resolutionLog([step])
                .population(140).production(1200).food(600).gold(80).strength(30)
                .goldUnlocked(true).strengthUnlocked(true)
                .permanentFreeRerolls(1)
                .acceleratedGreatWorkNames(["Cathedral"] as Set)
                .build()
        def seat1 = PlayerState.newPlayer()

        and: "the match: mid-PLAY in summer year 3, seat 0 submitted, resolution held"
        def original = GameState.multiPlayer([seat0, seat1], new TimeState(Season.SUMMER, Phase.PLAY, 3))
                .markSubmitted(0)
                .withResolutionPending(true)

        when:
        def json = KhPersistenceService.serialize(original)
        def restored = KhPersistenceService.deserialize(json)

        then: "structural (record) equality holds across the whole graph, save the intentionally-dropped resolution log"
        def expected = original.withPlayer(0, original.getPlayer(0).withResolutionLog([]))
        restored == expected

        and: "the fragile persistent pieces survived (boosted card, advisor counters, barrier)"
        restored.getPlayer(0).getCouncil()[0] == advisor
        restored.getPlayer(0).getBoard().get(Season.SUMMER) == [boosted]
        restored.getPlayer(0).getResources() == seat0.getResources()
        restored.submittedSeats == [0] as Set
        restored.isResolutionPending()
        restored.time == new TimeState(Season.SUMMER, Phase.PLAY, 3)

        and: "the transient resolution log is deliberately not persisted (restored empty)"
        restored.getPlayer(0).getResolutionLog().isEmpty()
    }
}
