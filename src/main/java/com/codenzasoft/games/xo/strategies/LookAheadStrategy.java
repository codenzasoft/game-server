package com.codenzasoft.games.xo.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.xo.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LookAheadStrategy implements XoGameListener {
  private static final Logger log = LoggerFactory.getLogger(LookAheadStrategy.class);

  private final Player player;
  private final Piece piece;
  private static final Random RANDOM = new Random();

  public LookAheadStrategy(final Player player, final Piece piece) {
    this.player = player;
    this.piece = piece;
  }

  void play(final XoGame game, final XoState state) {
    final Board board = game.getBoard();
    final List<Position> moves = state.moves().getOrDefault(piece, List.of());
    if (moves.isEmpty()) return;

    // On an empty board every opening move is equivalent under minimax — pick at random.
    if (board.getEmptyPositions().size() == 9) {
      final Position move = moves.get(RANDOM.nextInt(moves.size()));
      game.playPiece(player, move.row(), move.column());
      return;
    }

    int bestScore = Integer.MIN_VALUE;
    final List<Position> bestMoves = new ArrayList<>();
    for (final Position move : moves) {
      final int score = minimax(piece.opposite(), board.set(move, piece), false);
      if (score > bestScore) {
        bestScore = score;
        bestMoves.clear();
        bestMoves.add(move);
      } else if (score == bestScore) {
        bestMoves.add(move);
      }
    }

    final Position choice = bestMoves.get(RANDOM.nextInt(bestMoves.size()));
    log.debug("Choosing move {} with minimax score {}", choice, bestScore);
    game.playPiece(player, choice.row(), choice.column());
  }

  /**
   * Minimax: returns +1 if the bot wins from this position under optimal play by both sides, -1 if
   * the bot loses, and 0 for a draw. {@code maximizing} is true when it is the bot's turn.
   */
  private int minimax(final Piece current, final Board board, final boolean maximizing) {
    final Optional<Line> winner = board.getWinner();
    if (winner.isPresent()) {
      return winner.get().piece().equals(piece) ? 1 : -1;
    }
    final List<Position> empty = board.getEmptyPositions();
    if (empty.isEmpty()) {
      return 0;
    }
    if (maximizing) {
      int best = Integer.MIN_VALUE;
      for (final Position move : empty) {
        best = Math.max(best, minimax(current.opposite(), board.set(move, current), false));
      }
      return best;
    } else {
      int best = Integer.MAX_VALUE;
      for (final Position move : empty) {
        best = Math.min(best, minimax(current.opposite(), board.set(move, current), true));
      }
      return best;
    }
  }

  @Override
  public void gameStarted(final XoGame game, final XoState state) {
    play(game, state);
  }

  @Override
  public void gameChanged(final XoGame game, final XoState state) {
    play(game, state);
  }

  @Override
  public void gameOver(final XoGame game, final XoState state) {}

  @Override
  public void gameQuit(XoGame game, XoState state) {}
}
