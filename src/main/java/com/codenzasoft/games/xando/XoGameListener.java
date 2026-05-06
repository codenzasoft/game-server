package com.codenzasoft.games.xando;

public interface XoGameListener {

  void gameStarted(final XoGame game, final XoState state);

  void gameChanged(final XoGame game, final XoState state);

  void gameOver(final XoGame game, final XoState state);

  void gameQuit(final XoGame game, final XoState state);
}
