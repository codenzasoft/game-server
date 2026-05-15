package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.GameEntity;
import com.codenzasoft.games.spring.entities.GamePlayerEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.kh.KhGameEntity;
import com.codenzasoft.games.spring.entities.xo.XoGameEntity;
import com.codenzasoft.games.spring.entities.xo.XoPieceEntity;
import com.codenzasoft.games.spring.repositories.GamePlayersRepository;
import com.codenzasoft.games.spring.repositories.GamesRepository;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.services.xo.XoBroadcastService;
import com.codenzasoft.games.spring.services.xo.XoGamePersistenceService;
import com.codenzasoft.games.xo.Piece;
import com.codenzasoft.games.xo.Position;
import com.codenzasoft.games.xo.XoGame;
import com.codenzasoft.games.xo.strategies.LookAheadStrategy;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class GameService {
  private static final Logger log = LoggerFactory.getLogger(GameService.class);

  public static final int MAX_ACTIVE_GAMES = 10;

  @Autowired private GamesRepository gamesRepository;
  @Autowired private GamePlayersRepository gamePlayersRepository;
  @Autowired private UsersRepository usersRepository;
  @Autowired private XoGamePersistenceService xoGamePersistenceService;
  @Autowired private XoBroadcastService xoBroadcastService;
  @Autowired private ThreadPoolTaskExecutor gameNotificationExecutor;
  @Autowired private GameCache gameCache;

  /**
   * Returns whether the provided user has reached {@link #MAX_ACTIVE_GAMES} active (incomplete)
   * games.
   *
   * @param user The user to check.
   * @return {@code true} if the user is at or above the active-game limit.
   */
  public boolean isAtGameLimit(final UserEntity user) {
    return gamePlayersRepository.countActiveGamesByUser(user.getId()) >= MAX_ACTIVE_GAMES;
  }

  /**
   * Throws {@link IllegalStateException} if the provided user has reached the active game limit.
   *
   * @param user The user to check.
   */
  private void enforceGameLimit(final UserEntity user) {
    if (isAtGameLimit(user)) {
      throw new IllegalStateException(
          "Active game limit of " + MAX_ACTIVE_GAMES + " reached for user: " + user.getUsername());
    }
  }

  /**
   * Returns a new single user xo game for the provided {@link UserEntity} playing the provided
   * {@link Piece}.
   *
   * @param user The user.
   * @param piece The {@link Piece} associated with the user.
   * @param leader The {@link Piece} that starts the game.
   * @return A new game.
   * @throws IllegalStateException if the user has reached the active game limit.
   */
  @Transactional
  public XoGameEntity createXoGame(final UserEntity user, final Piece piece, final Piece leader) {
    enforceGameLimit(user);
    return createXoGame(Map.of(piece, user), leader);
  }

  /**
   * Returns a new single user kh game for the provided {@link UserEntity}.
   *
   * @param user The user.
   * @return A new game.
   * @throws IllegalStateException if the user has reached the active game limit.
   */
  @Transactional
  public KhGameEntity createKhGame(final UserEntity user) {
    enforceGameLimit(user);
    KhGameEntity game = new KhGameEntity(UUID.randomUUID());
    game.setPlayers(List.of(user));
    gamesRepository.save(game);
    return game;
  }

  /**
   * Creates a new game with assigned {@link Piece}s. Must specify at least a single {@link
   * UserEntity}. A bot will be added as needed.
   *
   * @param users The {@link UserEntity}s and corresponding {@link Piece}s.
   * @param leader The leading {@link Piece}
   * @return A new game.
   * @throws IllegalStateException if any human player has reached the active game limit.
   */
  @Transactional
  public XoGameEntity createXoGame(final Map<Piece, UserEntity> users, final Piece leader) {
    final XoGameEntity game = new XoGameEntity(UUID.randomUUID());
    if (users.isEmpty()) {
      throw new IllegalStateException("game must have at least one player");
    }
    if (users.size() > 2) {
      throw new IllegalStateException("game can only have two players");
    }

    users.values().stream().filter(u -> !u.isBot()).forEach(this::enforceGameLimit);

    final Map<Piece, UserEntity> players = new HashMap<>(users);

    // retrieve a bot as needed
    if (users.size() == 1) {
      final UserEntity bot = getBots(1).get(0);
      final Piece playerPiece = users.keySet().iterator().next();
      final Piece botPiece = playerPiece.opposite();
      players.put(botPiece, bot);
    }

    final List<UserEntity> playerOrder = new ArrayList<>();
    playerOrder.add(players.get(leader));
    playerOrder.add(players.get(leader.opposite()));
    game.setPlayers(playerOrder);

    final List<XoPieceEntity> pieces = new ArrayList<>();
    pieces.add(new XoPieceEntity(game.getId(), players.get(Piece.X).getId(), Piece.X));
    pieces.add(new XoPieceEntity(game.getId(), players.get(Piece.O).getId(), Piece.O));
    game.setGamePieces(pieces);
    gamesRepository.save(game);
    return game;
  }

  List<UserEntity> getBots(final int count) {
    List<UserEntity> bots = usersRepository.findByRolesRole(Role.BOT);
    if (bots.size() < count) {
      throw new IllegalStateException("Not enough bots to create a game");
    }
    return bots.subList(0, count);
  }

  /**
   * Creates and returns a next game based on the provided game. The leader of the next game is
   * based on the outcome of the current game (the loser gets to lead, or in case of a draw, the
   * lead switches to the next player).
   *
   * @param currentGame The current {@link XoGameEntity}
   * @return The next {@link XoGameEntity}
   */
  @Transactional
  public XoGameEntity createNextXoGame(final XoGame currentGame) {
    final Piece nextLeader =
        currentGame
            .getNextGameLeader()
            .orElseThrow(() -> new IllegalStateException("game is not over or quit"));
    final Map<Piece, UserEntity> players =
        Arrays.stream(Piece.values())
            .collect(
                Collectors.toMap(
                    piece -> piece,
                    piece ->
                        usersRepository.findById(currentGame.getPlayer(piece).id()).orElseThrow()));
    return createXoGame(players, nextLeader);
  }

  public List<GameEntity> getActiveGames(final UserEntity user) {
    return gamePlayersRepository.findActiveGamesByUser(user.getId()).stream()
        .map(GamePlayerEntity::getGame)
        .distinct()
        .sorted(Comparator.comparing(GameEntity::getCreatedAt).reversed())
        .toList();
  }

  /**
   * Marks the identified game as quit, removing it from the active games list. The requesting user
   * must be a player in the game. If the game is already complete or does not exist this is a
   * no-op.
   *
   * @param gameId The id of the game to delete.
   * @param user The user requesting the deletion.
   * @throws IllegalStateException if the user is not a player of the game.
   */
  @Transactional
  public void quitGame(final UUID gameId, final UserEntity user) {
    final Optional<GameEntity> optional = gamesRepository.findById(gameId);
    if (optional.isEmpty()) {
      return;
    }
    final GameEntity game = optional.get();
    final boolean isPlayer =
        game.getPlayers().stream().anyMatch(p -> p.getId().equals(user.getId()));
    if (!isPlayer) {
      throw new IllegalStateException(
          "User " + user.getUsername() + " is not a player of game " + gameId);
    }
    if (!game.isComplete()) {
      game.setQuitAt(new Date());
      gamesRepository.save(game);
      gameCache.removeGame(gameId);
    }
  }

  public List<UserEntity> getAvailableOpponents(final UserEntity currentUser) {
    return usersRepository.findAll().stream()
        .filter(u -> !u.getId().equals(currentUser.getId()))
        .filter(u -> !u.isBot())
        .sorted(Comparator.comparing(UserEntity::getUsername))
        .toList();
  }

  public Optional<XoGame> getOrBuildXoGame(final UUID id) {
    final Optional<Game<XoGame>> cached = gameCache.getGameOfType(id, GameType.XO);
    return cached
        .map(Game::getGame)
        .or(() -> findGame(id, XoGameEntity.class).map(this::buildXoGame));
  }

  public Optional<GameEngine> getOrBuildKhGame(final UUID id) {
    final Optional<Game<GameEngine>> cached = gameCache.getGameOfType(id, GameType.KH);
    return cached.map(Game::getGame).or(() -> findKhGame(id).map(this::buildKhGame));
  }

  public Optional<KhGameEntity> findKhGame(final UUID id) {
    return findGame(id, KhGameEntity.class);
  }

  public synchronized GameEngine buildKhGame(final KhGameEntity entity) {
    log.debug("Building KH game from entity: {}", entity.getId());
    final Optional<Game<GameEngine>> optionalGame =
        gameCache.getGameOfType(entity.getId(), GameType.KH);
    if (optionalGame.isPresent()) {
      return optionalGame.get().getGame();
    }

    final Player player = entity.getPlayers().get(0).toPlayer();
    final boolean isOver = entity.isOver() || entity.isQuit();
    final GameState state = GameState.builder().gameOver(isOver).build();
    final GameEngine engine = new GameEngine(entity.getId(), player, state);
    gameCache.addGame(engine);
    return engine;
  }

  public Optional<UserEntity> findUser(final UUID id) {
    return usersRepository.findById(id);
  }

  public <T extends GameEntity> Optional<T> findGame(final UUID id, final Class<T> clazz) {
    final Optional<GameEntity> gameEntity = gamesRepository.findById(id);
    if (gameEntity.isPresent()) {
      if (clazz.isAssignableFrom(gameEntity.get().getClass())) {
        return Optional.of(clazz.cast(gameEntity.get()));
      }
    }
    return Optional.empty();
  }

  // TODO: should synchronize on smaller scope
  public synchronized XoGame buildXoGame(final XoGameEntity gameEntity) {
    log.debug("Building XO game from entity: {}", gameEntity.getId());
    final Optional<Game<XoGame>> optionalGame =
        gameCache.getGameOfType(gameEntity.getId(), GameType.XO);
    if (optionalGame.isPresent()) {
      return optionalGame.get().getGame();
    }

    final Map<UUID, Piece> userIdToPiece =
        gameEntity.getGamePieces().stream()
            .collect(Collectors.toMap(XoPieceEntity::getUserId, XoPieceEntity::getPiece));

    final XoGame.Builder builder =
        new XoGame.Builder()
            .withId(gameEntity.getId())
            .withIsQuit(gameEntity.isQuit())
            .withNotificationExecutor(gameNotificationExecutor);
    final Map<Player, Piece> playerGamePieceMap =
        gameEntity.getGamePieces().stream()
            .collect(Collectors.toMap(gp -> gp.getUser().toPlayer(), XoPieceEntity::getPiece));

    gameEntity
        .getPlayers()
        .forEach(user -> builder.addPlayer(user.toPlayer(), userIdToPiece.get(user.getId())));
    builder.withLeader(gameEntity.getPlayers().stream().findFirst().get().toPlayer());
    gameEntity
        .getGameMoves()
        .forEach(
            gm -> {
              if (gm != null) {
                builder.addPlay(
                    gm.getUser().toPlayer(), new Position(gm.getMoveRow(), gm.getMoveColumn()));
              }
            });

    // add persistence first, broadcast second, followed by bot strategy
    builder.addListener(xoGamePersistenceService);
    builder.addListener(xoBroadcastService);
    for (final UserEntity user : gameEntity.getPlayers()) {
      if (user.isBot()) {
        builder.addListener(
            new LookAheadStrategy(user.toPlayer(), playerGamePieceMap.get(user.toPlayer())));
      }
    }
    final XoGame game = builder.build();
    gameCache.addGame(game);
    return game;
  }
}
