package com.codenzasoft.games.xando;

import com.codenzasoft.games.common.Player;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Describes the state of an X's and O's game.
 *
 * @param players Maps {@link Piece} to {@link Player} name.
 * @param board Maps {@link Piece} to occupied {@link Position}s in the order they were selected.
 * @param moves Maps {@link Piece} to possible {@link Position}s that can be selected next.
 * @param leader The {@link Piece} that started the game.
 * @param over Whether the game is over.
 * @param winner The {@link Piece} that won the game, possibly <code>null</code>.
 * @param line The {@link Line} that won the game, possible <code>null</code>.
 */
public record XoState(
    int sequence,
    Map<Piece, String> players,
    Map<Piece, List<Position>> board,
    Map<Piece, List<Position>> moves,
    Piece leader,
    boolean over,
    @JsonInclude(JsonInclude.Include.NON_NULL) Piece winner,
    @JsonInclude(JsonInclude.Include.NON_NULL) Line line) {}
