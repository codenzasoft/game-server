package com.codenzasoft.games.xando.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.xando.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LookAheadStrategy implements XsAndOsListener {
  private static final Logger log = LoggerFactory.getLogger(LookAheadStrategy.class);

  private final Player player;
  private final Piece piece;
  private static final Random RANDOM = new Random();

  public LookAheadStrategy(final Player player, final Piece piece) {
    this.player = player;
    this.piece = piece;
  }

  void play(final XsAndOs game, final State state) {
    final Board board = game.getBoard();
    if (board.getEmptyPositions().size() == 9) {
      // start of game, choose at random
      final List<Position> moves = state.moves().getOrDefault(piece, List.of());
      if (!moves.isEmpty()) {
        final Position move = moves.get(RANDOM.nextInt(moves.size()));
        game.playPiece(player, move.row(), move.column());
      }
    } else {
      // choose from all possible moves
      final List<Position> moves = state.moves().getOrDefault(piece, List.of());
      if (!moves.isEmpty()) {
        // try offense - play any square that will win
        for (final Position move : moves) {
          if (isWin(piece, move, board)) {
            log.info("Choosing OFFENSIVE move {}", move);
            game.playPiece(player, move.row(), move.column());
            return;
          }
        }

        // be defensive - block any opponent win
        for (final Position move : moves) {
          if (isWin(piece.opposite(), move, board)) {
            log.info("Choosing DEFENSIVE move {}", move);
            game.playPiece(player, move.row(), move.column());
            return;
          }
        }

        // next use look ahead
        WinLoss best = null;
        List<Position> choices = new ArrayList<>();
        for (final Position move : moves) {
          WinLoss record = getWinLoss(piece, move, board);
          if (best == null || record.compareTo(best) > 0) {
            best = record;
            choices.clear();
            choices.add(move);
          } else if (record.compareTo(best) == 0) {
            choices.add(move);
          }
        }
        if (!choices.isEmpty()) {
          final Position choice = choices.get(RANDOM.nextInt(choices.size()));
          game.playPiece(player, choice.row(), choice.column());
          log.info("Choosing BEST move {} with W/L record {}", choice, best);
        }
      }
    }
  }

  private boolean isWin(final Piece piece, final Position move, final Board board) {
    final Board nextBoard = board.set(move, piece);
    return nextBoard.getWinner().isPresent();
  }

  private WinLoss getWinLoss(final Piece piece, Position move, final Board board) {
    final Board nextBoard = board.set(move, piece);
    if (nextBoard.getWinner().isPresent()) {
      if (nextBoard.getWinner().get().piece().equals(this.piece)) {
        return new WinLoss(1, 0);
      } else {
        return new WinLoss(0, 1);
      }
    } else {
      final List<Position> nextMoves = nextBoard.getEmptyPositions();
      if (nextMoves.isEmpty()) {
        return new WinLoss(0, 0);
      }
      final Piece nextPiece = piece.opposite();
      WinLoss record = new WinLoss(0, 0);
      for (final Position nextMove : nextMoves) {
        record = record.add(getWinLoss(nextPiece, nextMove, nextBoard));
      }
      return record;
    }
  }

  @Override
  public void gameStarted(final XsAndOs game, final State state) {
    play(game, state);
  }

  @Override
  public void gameChanged(final XsAndOs game, final State state) {
    play(game, state);
  }

  @Override
  public void gameOver(final XsAndOs game, final State state) {}

  @Override
  public void gameQuit(XsAndOs game, State state) {}
}
