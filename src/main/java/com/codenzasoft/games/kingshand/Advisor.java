package com.codenzasoft.games.kingshand;

/**
 * Catalog definition of one of the 15 King's Hand advisors. Immutable, static, shared catalog data
 * — see {@link AdvisorCatalog}. Runtime state for an advisor that has actually been hired (current
 * boosted card, Reform stacks taken, Decree taken, proposal rotation bookkeeping) lives separately
 * on {@link SeatedAdvisor}.
 *
 * <p>Most advisors' Specialty is a plain RECURRING effect on {@link #baseCard()} that fires
 * automatically every season once seated — a seated advisor's card effects always resolve in full
 * and are never subject to the table's {@code StaffingTier} degradation, and seating an advisor
 * costs nothing beyond the upfront {@link #hiringCost()} gold fee paid at hire time.
 *
 * <p>Three advisors have a Specialty that the plain resource-effect system can't express on its
 * own, so it's modeled via small dedicated fields instead of {@code baseCard} effects:
 *
 * <ul>
 *   <li>{@link #freeRerollsWhileSeated()} — Maester Joric (free ADD-phase rerolls while seated).
 *   <li>{@link #extraAddCandidatesWhileSeated()} — Lady Iseult Varn (extra ADD-phase candidates
 *       while seated).
 *   <li>{@link #crossTableBoostAmount()} — Brynn Ostlar (permanently boosts the first
 *       Production-producing card on the currently-resolving season's table each season; the one
 *       advisor whose passive needs a cross-table reach, since today's CardTarget engine-builder
 *       effects only reach cards on the same table).
 * </ul>
 *
 * @param id stable catalog identifier, e.g. {@code "REEVE_ALDWIN"}; used as the {@code advisorId}
 *     key everywhere a seated advisor is referenced
 * @param displayName the advisor's full name, e.g. {@code "Reeve Aldwin"}
 * @param title the advisor's title, e.g. {@code "The Steward"}
 * @param specialtyDescription human-readable summary of the Specialty, for client display
 * @param hiringCost one-time gold cost to seat this advisor (deducted at hire time)
 * @param baseCard the advisor's underlying card — carries the Specialty's RECURRING resource
 *     effect(s), if any; advisors with a meta-only Specialty (Joric, Varn, Ostlar) have an empty
 *     effects list here
 * @param gate when this advisor becomes eligible to appear in the COUNCIL_ADD offer pool
 * @param reform the repeatable self-enhancement this advisor can propose; never {@code null}
 * @param decree the one-time civic project this advisor can propose; never {@code null}
 * @param crossTableBoostAmount base cross-table Production boost amount; 0 for every advisor except
 *     Brynn Ostlar
 * @param freeRerollsWhileSeated base count of additional free ADD-phase rerolls while seated; 0 for
 *     every advisor except Maester Joric
 * @param extraAddCandidatesWhileSeated base count of additional ADD-phase offer candidates while
 *     seated; 0 for every advisor except Lady Iseult Varn
 * @param ringColor art-direction ring-color key from the design doc (e.g. {@code "slate-grey"}),
 *     used by the client to render a colored placeholder ring around the portrait circle until real
 *     art exists
 * @param portraitPlaceholderId stable key the client maps to a placeholder portrait asset; replace
 *     with real per-advisor art later
 */
public record Advisor(
    String id,
    String displayName,
    String title,
    String specialtyDescription,
    int hiringCost,
    Card baseCard,
    AdvisorGate gate,
    Reform reform,
    Decree decree,
    int crossTableBoostAmount,
    int freeRerollsWhileSeated,
    int extraAddCandidatesWhileSeated,
    String ringColor,
    String portraitPlaceholderId) {}
