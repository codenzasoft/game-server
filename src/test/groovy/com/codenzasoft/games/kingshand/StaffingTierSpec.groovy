package com.codenzasoft.games.kingshand

import spock.lang.Specification

class StaffingTierSpec extends Specification {

    def "greaterThanOrEqualTo returns true when tier is equal"() {
        expect:
        tier.greaterThanOrEqualTo(tier)

        where:
        tier << [StaffingTier.NONE, StaffingTier.HALF, StaffingTier.FULL]
    }

    def "greaterThanOrEqualTo returns true when tier is strictly greater"() {
        expect:
        higher.greaterThanOrEqualTo(lower)

        where:
        higher            | lower
        StaffingTier.HALF | StaffingTier.NONE
        StaffingTier.FULL | StaffingTier.NONE
        StaffingTier.FULL | StaffingTier.HALF
    }

    def "greaterThanOrEqualTo returns false when tier is strictly lower"() {
        expect:
        !lower.greaterThanOrEqualTo(higher)

        where:
        lower             | higher
        StaffingTier.NONE | StaffingTier.HALF
        StaffingTier.NONE | StaffingTier.FULL
        StaffingTier.HALF | StaffingTier.FULL
    }
}
