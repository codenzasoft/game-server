package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.GameEntity;
import com.codenzasoft.games.spring.entities.GameStatus;
import com.codenzasoft.games.spring.entities.UserEntity;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class GameService {
  private static final Logger log = LoggerFactory.getLogger(GameService.class);

  @Autowired private GamesRepository gamesRepository;
  @Autowired private GamePlayersRepository gamePlayersRepository;
  @Autowired private UsersRepository usersRepository;
  @Autowired private XoGamePersistenceService xoGamePersistenceService;
  @Autowired private XoBroadcastService xoBroadcastService;
  @Autowired private ThreadPoolTaskExecutor gameNotificationExecutor;
  @Autowired private GameCache gameCache;

  /**
   * Returns a new single user game for the provided {@link UserEntity} playing the provided {@link
   * Piece}.
   *
   * @param id The game identifier.
   * @param user The user.
   * @param piece The {@link Piece} associated with the user.
   * @param leader The {@link Piece} that starts the game.
   * @return A new game.
   */
  @Transactional
  public XoGameEntity createXoGame(
      final UUID id, final UserEntity user, final Piece piece, final Piece leader) {
    return createXoGame(id, Map.of(piece, user), leader, user);
  }

  /**
   * Creates a new game with assigned {@link Piece}s. Must specify at least a single {@link
   * UserEntity}. A bot will be added as needed.
   *
   * @param id The game ID.
   * @param users The {@link UserEntity}s and corresponding {@link Piece}s.
   * @param leader The leading {@link Piece}
   * @return A new game.
   */
  @Transactional
  public XoGameEntity createXoGame(
      final UUID id, final Map<Piece, UserEntity> users, final Piece leader) {
    return createXoGame(id, users, leader, null);
  }

  @Transactional
  public XoGameEntity createXoGame(
      final UUID id,
      final Map<Piece, UserEntity> users,
      final Piece leader,
      final UserEntity creator) {
    final XoGameEntity entity = new XoGameEntity(id);
    entity.setCreatedBy(creator);
    if (users.isEmpty()) {
      throw new IllegalStateException("game must have at least one player");
    }
    if (users.size() > 2) {
      throw new IllegalStateException("game can only have two players");
    }

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
    entity.setPlayers(playerOrder);

    final List<XoPieceEntity> pieces = new ArrayList<>();
    pieces.add(new XoPieceEntity(id, players.get(Piece.X).getId(), Piece.X));
    pieces.add(new XoPieceEntity(id, players.get(Piece.O).getId(), Piece.O));
    entity.setGamePieces(pieces);
    gamesRepository.save(entity);
    return entity;
  }

  List<UserEntity> getBots(final int count) {
    List<UserEntity> bots = usersRepository.findByRolesRole(Role.BOT);
    if (bots.size() < count) {
      throw new IllegalStateException("Not enough bots to create a game");
    }
    return bots.subList(0, count);
  }

  @Transactional
  public XoGameEntity createNextXoGame(final UUID nextId, final XoGame currentGame) {
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
    return createXoGame(nextId, players, nextLeader);
  }

  @Transactional
  public XoGameEntity createXoInvite(
      final UUID id, final UserEntity creator, final UserEntity invitee) {
    final XoGameEntity entity = new XoGameEntity(id);
    entity.setStatus(GameStatus.PENDING);
    entity.setCreatedBy(creator);
    entity.setPlayers(List.of(creator, invitee));
    entity.setGamePieces(
        List.of(
            new XoPieceEntity(id, creator.getId(), Piece.X),
            new XoPieceEntity(id, invitee.getId(), Piece.O)));
    gamesRepository.save(entity);
    return entity;
  }

  @Transactional
  public XoGameEntity acceptInvite(final UUID gameId, final UserEntity actor) {
    final XoGameEntity game = findXoGame(gameId).orElseThrow();
    if (game.getStatus() != GameStatus.PENDING) {
      throw new IllegalStateException("game is not pending");
    }
    if (!game.getPlayers().contains(actor) || game.getCreatedBy().getId().equals(actor.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    game.setStatus(GameStatus.ACTIVE);
    gamesRepository.save(game);
    return game;
  }

  @Transactional
  public void declineInvite(final UUID gameId, final UserEntity actor) {
    final XoGameEntity game = findXoGame(gameId).orElseThrow();
    if (game.getStatus() != GameStatus.PENDING) {
      throw new IllegalStateException("game is not pending");
    }
    if (!game.getPlayers().contains(actor) || game.getCreatedBy().getId().equals(actor.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    game.setStatus(GameStatus.DECLINED);
    gamesRepository.save(game);
  }

  @Transactional
  public void cancelInvite(final UUID gameId, final UserEntity actor) {
    final XoGameEntity game = findXoGame(gameId).orElseThrow();
    if (game.getStatus() != GameStatus.PENDING) {
      throw new IllegalStateException("game is not pending");
    }
    if (!game.getCreatedBy().getId().equals(actor.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    game.setStatus(GameStatus.CANCELLED);
    gamesRepository.save(game);
  }

  public List<XoGameEntity> getPendingOutgoingInvites(final UserEntity user) {
    return gamePlayersRepository
        .findOutgoingByCreatorAndStatus(user.getId(), GameStatus.PENDING)
        .stream()
        .map(gp -> (XoGameEntity) gp.getGame())
        .distinct()
        .toList();
  }

  public List<XoGameEntity> getPendingIncomingInvites(final UserEntity user) {
    return gamePlayersRepository
        .findIncomingByUserAndStatus(user.getId(), GameStatus.PENDING)
        .stream()
        .map(gp -> (XoGameEntity) gp.getGame())
        .toList();
  }

  public List<UserEntity> getAvailableOpponents(final UserEntity currentUser) {
    return usersRepository.findAll().stream()
        .filter(u -> !u.getId().equals(currentUser.getId()))
        .filter(u -> !u.isBot())
        .sorted(Comparator.comparing(UserEntity::getUserName))
        .toList();
  }

  public Optional<XoGame> getOrBuildXoGame(final UUID id) {
    final Optional<Game<XoGame>> cached = gameCache.getGameOfType(id, GameType.XO);
    if (cached.isPresent()) {
      return Optional.of(cached.get().getGame());
    }
    return findXoGame(id)
        .filter(e -> e.getStatus() == GameStatus.ACTIVE)
        .map(this::buildTicTacToeGame);
  }

  public Optional<GameEntity> findGame(final UUID id) {
    return gamesRepository.findById(id);
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

  public Optional<XoGameEntity> findXoGame(final UUID id) {
    return findGame(id, XoGameEntity.class);
  }

  // TODO: should synchronize on smaller scope
  public synchronized XoGame buildTicTacToeGame(final XoGameEntity gameEntity) {
    log.info("Building XO game from entity: {}", gameEntity.getId());
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
