package com.codenzasoft.games.kingshand;

/**
 * A one-time civic project an advisor can propose during a COUNCIL_MEETING. Unlike a {@link
 * Reform}, a Decree can only ever be taken once per seated advisor (tracked by {@code
 * SeatedAdvisor.decreeTaken}).
 *
 * @param name flavor name shown to the player, e.g. {@code "Found the Town Hall"}
 * @param type what kind of one-time effect this Decree applies
 * @param targetGreatWorkName for {@link DecreeType#GREAT_WORK_BOOST} only: the stable name of the
 *     completed Great Work card to boost (also doubles as the availability gate — the decree isn't
 *     offered until that card is completed somewhere on the board); {@code null} otherwise
 * @param effectResource for {@link DecreeType#PASSIVE_BOOST} and {@link
 *     DecreeType#GREAT_WORK_BOOST}: which resource effect to boost; {@code null} for the other
 *     types
 * @param effectAmount magnitude of the one-time effect
 */
public record Decree(
    String name,
    DecreeType type,
    String targetGreatWorkName,
    ResourceTarget effectResource,
    double effectAmount) {}
