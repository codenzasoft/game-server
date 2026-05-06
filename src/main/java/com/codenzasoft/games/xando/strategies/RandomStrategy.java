package com.codenzasoft.games.xando.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.xando.*;
import java.util.List;
import java.util.Random;

public class RandomStrategy implements XoGameListener {

  private final Player player;
  private final Piece piece;
  private static final Random RANDOM = new Random();

  public RandomStrategy(final Player player, final Piece piece) {
    this.player = player;
    this.piece = piece;
  }

  void play(final XoGame game, final XoState state) {
    final List<Position> moves = state.moves().getOrDefault(piece, List.of());
    if (!moves.isEmpty()) {
      final Position move = moves.get(RANDOM.nextInt(moves.size()));
      game.playPiece(player, move.row(), move.column());
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
