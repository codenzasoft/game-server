package com.codenzasoft.games.xo;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.GameLifecycleObserver;
import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.xo.notifications.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XoGame implements Game<XoGame> {

  public static class Builder {
    private Player leader;
    private boolean quit;
    private final Map<Player, Piece> pieces = new HashMap<>();
    private final List<Player> players = new ArrayList<>();
    private final List<XoGameListener> listeners = new ArrayList<>();
    private final List<GameLifecycleObserver<XoGame>> lifecycleObservers = new ArrayList<>();
    private Executor executor;
    private UUID id;
    private final Map<Player, List<Position>> plays = new HashMap<>();

    public Builder withLeader(final Player leader) {
      this.leader = leader;
      return this;
    }

    public Builder withId(final UUID id) {
      this.id = id;
      return this;
    }

    public Builder withNotificationExecutor(final Executor executor) {
      this.executor = executor;
      return this;
    }

    public Builder addPlayer(final Player player, final Piece piece) {
      pieces.put(player, piece);
      players.add(player);
      return this;
    }

    public Builder addPlay(final Player player, final Position position) {
      List<Position> positions = plays.computeIfAbsent(player, k -> new ArrayList<>());
      positions.add(position);
      return this;
    }

    public Builder withIsQuit(final boolean isQuit) {
      this.quit = isQuit;
      return this;
    }

    public Builder addListener(final XoGameListener listener) {
      listeners.add(listener);
      return this;
    }

    /**
     * Registers a common {@link GameLifecycleObserver}, notified of game-type-agnostic lifecycle
     * events (started, resumed, over, quit) alongside the XO-specific {@link XoGameListener}s.
     */
    public Builder addLifecycleObserver(final GameLifecycleObserver<XoGame> observer) {
      lifecycleObservers.add(observer);
      return this;
    }

    public XoGame build() {
      if (id == null) {
        throw new IllegalStateException("must specify a game id");
      }
      if (players.stream().distinct().count() != 2) {
        throw new IllegalArgumentException("requires two players");
      }
      if (pieces.values().stream().distinct().count() != players.size()) {
        throw new IllegalArgumentException("each player must have distinct board piece");
      }
      if (leader == null) {
        withLeader(players.get(0));
      }
      if (executor == null) {
        withNotificationExecutor(Executors.newSingleThreadExecutor());
      }
      return new XoGame(
          id,
          Board.emptyBoard(pieces.get(leader)),
          players,
          pieces,
          listeners,
          lifecycleObservers,
          executor,
          plays,
          quit);
    }
  }

  private static final Logger log = LoggerFactory.getLogger(XoGame.class.getName());

  private static final XoStartedNotification STARTED_NOTIFICATION = new XoStartedNotification();
  private static final XoOverNotification ENDED_NOTIFICATION = new XoOverNotification();
  private static final XoChangedNotification CHANGED_NOTIFICATION = new XoChangedNotification();
  private static final XoQuitNotification QUIT_NOTIFICATION = new XoQuitNotification();

  private final UUID id;
  private final Map<Player, Piece> pieces;
  private final List<Player> players;
  private final List<XoGameListener> listeners = new ArrayList<>();
  private final List<GameLifecycleObserver<XoGame>> lifecycleObservers = new ArrayList<>();
  private Board board;
  private boolean quit = false;
  private Player quitter = null;
  private int nextPlayerIndex = 0;
  private final Executor executor;

  private XoGame(
      final UUID id,
      final Board board,
      final List<Player> players,
      final Map<Player, Piece> pieces,
      final List<XoGameListener> listeners,
      final List<GameLifecycleObserver<XoGame>> lifecycleObservers,
      final Executor executor,
      final Map<Player, List<Position>> plays,
      final boolean isQuit) {
    this.id = id;
    this.board = board;
    this.players = List.copyOf(players);
    this.pieces = Collections.unmodifiableMap(pieces);
    this.executor = executor;
    this.initializeLeader();
    this.initialize(plays);
    // must set after initializing plays, to allows plays to work
    this.quit = isQuit;
    // IMPORTANT order - avoid notifications while re-creating state
    // TODO: if the game is over or quit, don't add listeners? (there are no notifications)
    this.listeners.addAll(listeners);
    if (lifecycleObservers != null) {
      this.lifecycleObservers.addAll(lifecycleObservers);
    }
    // TODO: should there be a resume notification? XO does not persist a snapshot — every rebuild
    // replays the full move history from scratch (see GameService.buildXoGame), so there is no
    // reliable signal to distinguish a true resume from this first build. gameResumed() exists on
    // GameLifecycleObserver and can be fired here once that distinction is available; for now
    // every (re)build is treated as a fresh start (mirrors the same gap in KH's GameEngine).
    notifyListeners(STARTED_NOTIFICATION);
    notifyLifecycleObservers(GameLifecycleObserver::gameStarted);
  }

  private void initializeLeader() {
    final Optional<Player> leader =
        pieces.entrySet().stream()
            .filter(e -> e.getValue().equals(board.leader()))
            .findFirst()
            .map(Map.Entry::getKey);
    leader.ifPresent(player -> nextPlayerIndex = players.indexOf(player));
  }

  private void initialize(final Map<Player, List<Position>> plays) {
    while (!plays.isEmpty()) {
      final Player player = getPlayer();
      final List<Position> positions = plays.get(player);
      if (positions != null) {
        if (positions.isEmpty()) {
          plays.remove(player);
        } else {
          final Position position = positions.remove(0);
          playPiece(player, position.row(), position.column());
          if (positions.isEmpty()) {
            plays.remove(player);
          }
        }
      }
    }
  }

  public GameType getGameType() {
    return GameType.XO;
  }

  public XoGame getGame() {
    return this;
  }

  public UUID getId() {
    return id;
  }

  public List<Player> getPlayers() {
    return players;
  }

  void notifyListeners(final XoNotification notification) {
    final XoState state = buildState();
    final XoGame game = this;
    for (final XoGameListener listener : listeners) {
      executor.execute(() -> notification.execute(game, state, listener));
    }
  }

  /**
   * Notifies every registered common {@link GameLifecycleObserver} via the executor. {@code call}
   * is typically a method reference, e.g. {@code GameLifecycleObserver::gameStarted}.
   */
  private void notifyLifecycleObservers(
      final BiConsumer<GameLifecycleObserver<XoGame>, Game<XoGame>> call) {
    final Game<XoGame> game = this;
    for (final GameLifecycleObserver<XoGame> observer : lifecycleObservers) {
      executor.execute(() -> call.accept(observer, game));
    }
  }

  public synchronized Board getBoard() {
    return board;
  }

  public boolean isOverOrQuit() {
    return isOver() || isQuit();
  }

  public synchronized boolean isOver() {
    return board.getWinner().isPresent() || board.getEmptyPositions().isEmpty();
  }

  public synchronized boolean isQuit() {
    return quit;
  }

  private Player getPlayer() {
    return players.get(nextPlayerIndex);
  }

  public Optional<Line> getWinner() {
    return board.getWinner();
  }

  public synchronized void quit(final Player player) {
    if (isOver() || isQuit()) {
      return;
    }
    quit = true;
    quitter = player;
    notifyListeners(QUIT_NOTIFICATION);
    notifyLifecycleObservers(GameLifecycleObserver::gameQuit);
  }

  public synchronized void playPiece(final Player player, int row, int col) {
    if (isQuit()) {
      throw new IllegalStateException("unable to play - game already quit");
    }
    if (isOver()) {
      throw new IllegalStateException("unable to play - game is over");
    }
    final Piece piece = pieces.get(player);
    if (piece == null) {
      throw new IllegalArgumentException("unable to play - player not found");
    }
    final Player nextPlayer = getPlayer();
    if (!player.equals(nextPlayer)) {
      throw new IllegalArgumentException("unable to play - player is out of order");
    }
    board = board.set(Position.of(row, col), piece);
    log.debug("'{}' playing '{}' at ({},{})", player.name(), piece, row, col);
    if (board.getWinner().isPresent() || board.getEmptyPositions().isEmpty()) {
      notifyListeners(ENDED_NOTIFICATION);
      notifyLifecycleObservers(GameLifecycleObserver::gameOver);
      // TODO: clear listeners and any other cleanup
    } else {
      nextPlayerIndex++;
      if (nextPlayerIndex >= players.size()) {
        nextPlayerIndex = 0;
      }
      notifyListeners(CHANGED_NOTIFICATION);
    }
  }

  private Map<Piece, Player> getPlayerMap() {
    return pieces.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
  }

  public synchronized XoState buildState() {
    final Map<Piece, Player> playerMap = getPlayerMap();
    final Map<Piece, List<Position>> positions =
        Arrays.stream(Piece.values())
            .collect(Collectors.toMap(piece -> piece, piece -> board.getPositions(piece)));
    final Optional<Line> winningLine = board.getWinner();
    final XoState state;
    final Map<Piece, String> names =
        playerMap.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, key -> key.getValue().name()));
    if (winningLine.isPresent()) {
      final Line line = winningLine.get();
      state =
          new XoState(
              board.sequence(),
              names,
              positions,
              Map.of(),
              board.leader(),
              isOverOrQuit(),
              quitter != null ? pieces.get(quitter) : null,
              line.piece(),
              line);
    } else {
      final Player next = getPlayer();
      final Map<Piece, List<Position>> moves = Map.of(pieces.get(next), board.getEmptyPositions());
      state =
          new XoState(
              board.sequence(),
              names,
              positions,
              moves,
              board.leader(),
              isOverOrQuit(),
              quitter != null ? pieces.get(quitter) : null,
              null,
              null);
    }
    return state;
  }

  public Piece getPiece(final Player player) {
    return pieces.get(player);
  }

  public Player getPlayer(final Piece piece) {
    return pieces.entrySet().stream()
        .filter(entry -> entry.getValue().equals(piece))
        .findFirst()
        .map(Map.Entry::getKey)
        .orElse(null);
  }

  public Optional<Piece> getNextGameLeader() {
    if (isOverOrQuit()) {
      final Optional<Line> optionalLine = board.getWinner();
      if (optionalLine.isPresent()) {
        final Line line = optionalLine.get();
        return Optional.of(line.piece().opposite());
      } else {
        return Optional.of(board.leader().opposite());
      }
    }
    return Optional.empty();
  }
}
