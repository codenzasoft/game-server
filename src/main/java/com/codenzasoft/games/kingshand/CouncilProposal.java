package com.codenzasoft.games.kingshand;

/**
 * The single rotating project proposal presented during a COUNCIL_MEETING phase, or {@code null}
 * (omitted from JSON) if no seated advisor currently has anything left to propose.
 *
 * @param advisorId the {@link Advisor#id()} of the proposing advisor
 * @param kind whether this is a {@link Reform} or {@link Decree} proposal
 * @param name the flavor name of the Reform or Decree being proposed (for client display)
 */
public record CouncilProposal(String advisorId, ProposalKind kind, String name) {}
