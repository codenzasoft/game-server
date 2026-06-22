package com.codenzasoft.games.kingshand;

/**
 * A repeatable self-enhancement an advisor can propose during a COUNCIL_MEETING, capped at {@link
 * #maxStacks()} acceptances over the life of a run. Every stack grants one instance of the benefit
 * described by {@link #benefitType()} (sized by {@link #benefitPerStack()}) and pays a permanent
 * cost — {@link #costPerStack()} (a negative magnitude) added to the advisor's own card's {@link
 * #costResource()} effect.
 *
 * @param name flavor name shown to the player, e.g. {@code "Audit the Granaries"}
 * @param maxStacks maximum number of times this Reform may be accepted (3 for most advisors, 1 for
 *     the strongest ones per the design doc)
 * @param benefitType what kind of benefit each stack grants
 * @param benefitResource for {@link ReformBenefitType#CARD_EFFECT} only: which resource on the
 *     advisor's own card gets boosted; {@code null} for the other benefit types
 * @param benefitPerStack magnitude of the benefit granted per stack
 * @param costResource which resource on the advisor's own card is permanently drained per stack
 * @param costPerStack magnitude of the cost per stack; stored as a negative number so it can be
 *     applied directly via {@link Card#withEffectBoostOrAdd}
 */
public record Reform(
    String name,
    int maxStacks,
    ReformBenefitType benefitType,
    ResourceTarget benefitResource,
    double benefitPerStack,
    ResourceTarget costResource,
    double costPerStack) {}
