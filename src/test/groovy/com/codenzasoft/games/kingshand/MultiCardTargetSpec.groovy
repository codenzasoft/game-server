package com.codenzasoft.games.kingshand

import spock.lang.Specification

/**
 * Reproduction for the production "card not found in deck: FARMSTEAD" crash during PLAY resolution.
 * A card with two {@link CardTarget} effects (TRADING_POST: boost PRODUCTION cards, then FOOD cards)
 * played with a single card that matches both (FARMSTEAD produces food AND production) throws,
 * because {@code Card.play} computes its target list once and reuses the stale reference after the
 * first effect has already replaced the target with a boosted, no-longer-record-equal version.
 */
class MultiCardTargetSpec extends Specification {

    def "a card with two CardTarget effects boosting one dual-resource card resolves without throwing"() {
        given: "TRADING_POST (2 CardTarget effects) sits before FARMSTEAD (food + production) on the table"
        def tradingPost = CardCatalog.TRADING_POST
        def farmstead = CardCatalog.FARMSTEAD
        def ps = PlayerState.builder()
                .table(Season.SUMMER, [tradingPost, farmstead])
                .population(100000).production(1000000).food(1000000)
                .build()

        when: "TRADING_POST resolves, targeting cards after it on the board"
        def result = tradingPost.play(ps, Season.SUMMER, StaffingTier.FULL, SabotageContext.none())

        then: "it must not throw — both effects should stack on the same FARMSTEAD"
        noExceptionThrown()
        result != null
    }
}
