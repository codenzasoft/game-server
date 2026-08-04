package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.GameLifecycleObserver;
import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.strategies.AdaptiveStrategy;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.strategies.KhBeamSearchStrategy;
import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.GameEntity;
import com.codenzasoft.games.spring.entities.GamePlayerEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.UserGamesPlayedEntity;
import com.codenzasoft.games.spring.entities.hearts.HeartsGameEntity;
import com.codenzasoft.games.spring.entities.kh.KhGameEntity;
import com.codenzasoft.games.spring.entities.xo.XoGameEntity;
import com.codenzasoft.games.spring.entities.xo.XoPieceEntity;
import com.codenzasoft.games.spring.repositories.GamePlayersRepository;
import com.codenzasoft.games.spring.repositories.GamesRepository;
import com.codenzasoft.games.spring.repositories.UserGamesPlayedRepository;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.repositories.hearts.HeartsGameRepository;
import com.codenzasoft.games.spring.services.hearts.HeartsBroadcastService;
import com.codenzasoft.games.spring.services.hearts.HeartsPersistenceService;
import com.codenzasoft.games.spring.services.kh.KhBroadcastService;
import com.codenzasoft.games.spring.services.xo.XoBroadcastService;
import com.codenzasoft.games.spring.services.xo.XoGamePersistenceService;
import com.codenzasoft.games.xo.Line;
import com.codenzasoft.games.xo.Piece;
import com.codenzasoft.games.xo.Position;
import com.codenzasoft.games.xo.XoGame;
import com.codenzasoft.games.xo.strategies.LookAheadStrategy;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.Date;
import java.util.concurrent.Executors;
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
  private static final List<String> BOT_NAMES = List.of("meg-bot", "tom-bot", "hex-bot");

  @Autowired private GamesRepository gamesRepository;
  @Autowired private GamePlayersRepository gamePlayersRepository;
  @Autowired private UsersRepository usersRepository;
  @Autowired private XoGamePersistenceService xoGamePersistenceService;
  @Autowired private HeartsGameRepository heartsGameRepository;
  @Autowired private HeartsBroadcastService heartsBroadcastService;
  @Autowired private KhBroadcastService khBroadcastService;
  @Autowired private HeartsPersistenceService heartsPersistenceService;
  @Autowired private XoBroadcastService xoBroadcastService;
  @Autowired private ThreadPoolTaskExecutor gameNotificationExecutor;
  @Autowired private GameCache gameCache;
  @Autowired private UserService userService;
  @Autowired private UserGamesPlayedRepository userGamesPlayedRepository;

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
   * Creates a pending multiplayer XO game for the provided host with no opponent yet. The host is
   * assigned {@link Piece#X} and goes first. A second player may join via {@link #joinXoGame}.
   *
   * @param user The host player.
   * @return A new pending game entity with exactly one player.
   * @throws IllegalStateException if the user has reached the active game limit.
   */
  @Transactional
  public XoGameEntity createPendingXoGame(final UserEntity user) {
    enforceGameLimit(user);
    final XoGameEntity game = new XoGameEntity(UUID.randomUUID());
    game.setPlayers(List.of(user));
    game.setGamePieces(List.of(new XoPieceEntity(game.getId(), user.getId(), Piece.X)));
    gamesRepository.save(game);
    return game;
  }

  /**
   * Returns the {@link XoGameEntity} with the provided ID, or {@code empty} if not found.
   *
   * @param id A game {@link UUID}.
   * @return The entity, or {@code empty}.
   */
  public Optional<XoGameEntity> findXoGame(final UUID id) {
    return findGameEntity(id, XoGameEntity.class);
  }

  /**
   * Adds the provided user as the {@link Piece#O} player in an existing pending XO game. If the
   * user is already a player this is a no-op and the unmodified entity is returned. The game must
   * be active and not yet full (i.e. have fewer than 2 pieces).
   *
   * @param gameId The pending game to join.
   * @param user The user joining.
   * @return The updated game entity.
   * @throws IllegalStateException if the game is full, complete, or not found.
   */
  @Transactional
  public XoGameEntity joinXoGame(final UUID gameId, final UserEntity user) {
    final XoGameEntity game =
        findGameEntity(gameId, XoGameEntity.class)
            .orElseThrow(() -> new IllegalStateException("Game not found: " + gameId));
    if (game.isComplete()) throw new IllegalStateException("Cannot join a completed game");
    if (game.getGamePieces().size() >= 2) throw new IllegalStateException("Game is already full");
    if (game.getGamePieces().stream().anyMatch(p -> p.getUserId().equals(user.getId())))
      return game;
    enforceGameLimit(user);
    final List<UserEntity> players = new ArrayList<>(game.getPlayers());
    players.add(user);
    game.setPlayers(players);
    final List<XoPieceEntity> pieces = new ArrayList<>(game.getGamePieces());
    pieces.add(new XoPieceEntity(game.getId(), user.getId(), Piece.O));
    game.setGamePieces(pieces);
    gamesRepository.save(game);
    return game;
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
    // Two-seat match: the creator takes seat 0, a bot fills seat 1. Seat resolution and per-seat
    // broadcasting are generic, so replacing the bot with a second human later is a game-creation
    // concern only.
    final List<UserEntity> players = new ArrayList<>();
    players.add(user);
    players.addAll(getBots(1));
    game.setPlayers(players);
    gamesRepository.save(game);
    return game;
  }

  /**
   * Returns a new Hearts game for the provided {@link UserEntity} playing against three bots.
   *
   * @param user The human player.
   * @return A new game entity.
   * @throws IllegalStateException if the user has reached the active game limit or fewer than three
   *     bots are registered.
   */
  @Transactional
  public HeartsGameEntity createHeartsGame(final UserEntity user) {
    enforceGameLimit(user);
    final List<UserEntity> bots = getBots(3);
    final HeartsGameEntity game = new HeartsGameEntity(UUID.randomUUID());
    final List<UserEntity> players = new ArrayList<>();
    players.add(user);
    players.addAll(bots);
    game.setPlayers(players);
    heartsGameRepository.save(game);
    return game;
  }

  @Transactional
  public HeartsGameEntity createNextHeartsGame(final HeartsGame currentGame) {
    final List<UserEntity> players =
        currentGame.getPlayers().stream()
            .map(player -> usersRepository.findById(player.id()).orElseThrow())
            .toList();
    players.stream().filter(u -> !u.isBot()).forEach(this::enforceGameLimit);
    final HeartsGameEntity game = new HeartsGameEntity(UUID.randomUUID());
    game.setPlayers(players);
    heartsGameRepository.save(game);
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
      final List<String> existing = bots.stream().map(UserEntity::getUsername).toList();
      final List<String> newNames = new ArrayList<>(BOT_NAMES);
      newNames.removeAll(existing);
      final int createCount = count - bots.size();
      if (newNames.size() >= createCount) {
        for (int i = 0; i < createCount; i++) {
          userService.createBot(newNames.get(i));
        }
        return getBots(count);
      } else {
        throw new IllegalStateException("Not enough bots to create a game");
      }
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
    }
    gameCache.removeGame(gameId);
  }

  /**
   * Returns an {@link XoGame} with the provided ID or <code>empty</code> if a game with the
   * provided ID does not exist. The game is retrieved from the {@link GameCache} if present,
   * otherwise it is built from the persisted entity and added to the cache.
   *
   * @param id A game {@link UUID}.
   * @return An {@link XoGame} or <code>empty</code> if a game with the provided id does not exist.
   */
  public Optional<XoGame> getOrBuildXoGame(final UUID id) {
    final Optional<Game<XoGame>> cached = gameCache.getGameOfType(id, GameType.XO);
    if (cached.isPresent()) {
      return cached.map(Game::getGame);
    }
    final Optional<XoGameEntity> persisted = findGameEntity(id, XoGameEntity.class);
    if (persisted.isPresent()) {
      if (persisted.get().getGamePieces().size() < 2) {
        log.warn(
            "getOrBuildXoGame: game {} is pending ({} pieces)",
            id,
            persisted.get().getGamePieces().size());
        return Optional.empty();
      }
      final XoGame game = buildXoGame(persisted.get());
      gameCache.addGame(game);
      return Optional.of(game);
    }
    return Optional.empty();
  }

  /**
   * Returns an {@link GameEngine} with the provided ID or <code>empty</code> if a game with the
   * provided ID does not exist. The game is retrieved from the {@link GameCache} if present,
   * otherwise it is built from the persisted entity and added to the cache.
   *
   * @param id A game {@link UUID}.
   * @return An {@link GameEngine} or <code>empty</code> if a game with the provided id does not
   *     exist.
   */
  public Optional<GameEngine> getOrBuildKhGame(final UUID id) {
    final Optional<Game<GameEngine>> cached = gameCache.getGameOfType(id, GameType.KH);
    if (cached.isPresent()) {
      return cached.map(Game::getGame);
    }
    final Optional<KhGameEntity> persisted = findGameEntity(id, KhGameEntity.class);
    if (persisted.isPresent()) {
      final GameEngine game = buildKhGame(persisted.get());
      gameCache.addGame(game);
      return Optional.of(game);
    }
    return Optional.empty();
  }

  /**
   * Returns a {@link HeartsGame} with the provided ID or {@code empty} if a game with the provided
   * ID does not exist or is still in the lobby (fewer than 4 players). The game is retrieved from
   * the {@link GameCache} if present, otherwise it is built from the persisted entity and added to
   * the cache.
   *
   * @param id A game {@link UUID}.
   * @return A {@link HeartsGame} or {@code empty} if the game does not exist or is not yet started.
   */
  public Optional<HeartsGame> getOrBuildHeartsGame(final UUID id) {
    final Optional<Game<HeartsGame>> cached = gameCache.getGameOfType(id, GameType.HEARTS);
    if (cached.isPresent()) {
      return cached.map(Game::getGame);
    }
    final Optional<HeartsGameEntity> persisted = findGameEntity(id, HeartsGameEntity.class);
    if (persisted.isEmpty()) return Optional.empty();
    final HeartsGameEntity entity = persisted.get();
    if (entity.getPlayers().size() < 4) {
      log.warn(
          "getOrBuildHeartsGame: game {} is pending ({} players)", id, entity.getPlayers().size());
      return Optional.empty();
    }
    final Optional<HeartsState> restoredState =
        heartsPersistenceService.reconstructState(id, entity.getPlayers());
    final HeartsGame game;
    if (restoredState.isPresent()) {
      log.info("getOrBuildHeartsGame: restoring game {} from persisted state", id);
      game = buildRestoredHeartsGame(entity, restoredState.get());
      game.resumePlay();
    } else {
      game = buildHeartsGame(entity);
    }
    gameCache.addGame(game);
    return Optional.of(game);
  }

  /**
   * Returns the {@link HeartsGameEntity} with the provided ID, or {@code empty} if not found.
   *
   * @param id A game {@link UUID}.
   * @return The entity, or {@code empty}.
   */
  public Optional<HeartsGameEntity> findHeartsGame(final UUID id) {
    return findGameEntity(id, HeartsGameEntity.class);
  }

  /**
   * Creates a pending Hearts game for the provided host with no bots. Other human players may join
   * via {@link #joinHeartsGame} before the host starts the game via {@link #startHeartsGame}.
   *
   * @param user The host player.
   * @return A new pending game entity with exactly one player.
   * @throws IllegalStateException if the user has reached the active game limit.
   */
  @Transactional
  public HeartsGameEntity createPendingHeartsGame(final UserEntity user) {
    enforceGameLimit(user);
    final HeartsGameEntity game = new HeartsGameEntity(UUID.randomUUID());
    game.setPlayers(List.of(user));
    heartsGameRepository.save(game);
    return game;
  }

  /**
   * Adds the provided user to an existing pending Hearts game. If the user is already a player this
   * is a no-op and the unmodified entity is returned. The game must be active and not yet full.
   *
   * @param gameId The pending game to join.
   * @param user The user joining.
   * @return The updated game entity.
   * @throws IllegalStateException if the game is full, complete, or not found.
   */
  @Transactional
  public HeartsGameEntity joinHeartsGame(final UUID gameId, final UserEntity user) {
    final HeartsGameEntity game =
        heartsGameRepository
            .findById(gameId)
            .orElseThrow(() -> new IllegalStateException("Game not found: " + gameId));
    if (game.isComplete()) throw new IllegalStateException("Cannot join a completed game");
    if (game.getPlayers().size() >= 4) throw new IllegalStateException("Game is already full");
    if (game.getPlayers().stream().anyMatch(p -> p.getId().equals(user.getId()))) return game;
    enforceGameLimit(user);
    final List<UserEntity> players = new ArrayList<>(game.getPlayers());
    players.add(user);
    game.setPlayers(players);
    heartsGameRepository.save(game);
    return game;
  }

  /**
   * Fills any remaining seats with bots so the game can proceed. The requesting user must already
   * be a player. If the game already has 4 players this is a no-op.
   *
   * @param gameId The pending game to start.
   * @param user The user requesting the start.
   * @return The updated game entity with exactly 4 players.
   * @throws IllegalStateException if the game is not found, already complete, or the user is not a
   *     player.
   */
  @Transactional
  public HeartsGameEntity startHeartsGame(final UUID gameId, final UserEntity user) {
    final HeartsGameEntity game =
        heartsGameRepository
            .findById(gameId)
            .orElseThrow(() -> new IllegalStateException("Game not found: " + gameId));
    if (game.isComplete()) throw new IllegalStateException("Cannot start a completed game");
    if (game.getPlayers().stream().noneMatch(p -> p.getId().equals(user.getId())))
      throw new IllegalStateException(
          "User " + user.getUsername() + " is not a player of game " + gameId);
    final int botsNeeded = 4 - game.getPlayers().size();
    if (botsNeeded > 0) {
      final List<UserEntity> players = new ArrayList<>(game.getPlayers());
      players.addAll(getBots(botsNeeded));
      game.setPlayers(players);
      heartsGameRepository.save(game);
    }
    return game;
  }

  protected synchronized HeartsGame buildHeartsGame(final HeartsGameEntity entity) {
    log.debug("Building Hearts game from entity: {}", entity.getId());
    final List<Player> players = entity.getPlayers().stream().map(UserEntity::toPlayer).toList();
    final HeartsGame.Builder builder =
        new HeartsGame.Builder()
            .withId(entity.getId())
            .withPlayers(players)
            .withNotificationExecutor(Executors.newSingleThreadExecutor())
            .addListener(heartsPersistenceService)
            .addListener(heartsBroadcastService)
            .addLifecycleObserver(new GameLifecycleListener<>());
    for (final UserEntity user : entity.getPlayers()) {
      if (user.isBot()) {
        builder.addPlayerObserver(user.toPlayer(), new AdaptiveStrategy());
      }
    }
    return builder.build();
  }

  protected synchronized HeartsGame buildRestoredHeartsGame(
      final HeartsGameEntity entity, final HeartsState state) {
    log.debug("Restoring Hearts game {} from persisted state", entity.getId());
    final List<Player> players = entity.getPlayers().stream().map(UserEntity::toPlayer).toList();
    final HeartsGame.Builder builder =
        new HeartsGame.Builder()
            .withId(entity.getId())
            .withPlayers(players)
            .withRestoredState(state)
            .withNotificationExecutor(Executors.newSingleThreadExecutor())
            .addListener(heartsPersistenceService)
            .addListener(heartsBroadcastService)
            .addLifecycleObserver(new GameLifecycleListener<>());
    for (final UserEntity user : entity.getPlayers()) {
      if (user.isBot()) {
        builder.addPlayerObserver(user.toPlayer(), new AdaptiveStrategy());
      }
    }
    return builder.build();
  }

  protected synchronized GameEngine buildKhGame(final KhGameEntity entity) {
    log.debug("Building KH game from entity: {}", entity.getId());
    final List<UserEntity> seatedUsers = entity.getPlayers();
    // One kingdom per seat, sharing a clock. Both kingdoms are seeded with the standard starting
    // deck by the engine's initialize()/seedStartingDeck(); the engine's activeSeat is set per
    // request by KhController based on the acting user's seat.
    final GameState state = GameState.newMultiplayer(Math.max(seatedUsers.size(), 1));
    // Built via the Builder so the lifecycle listener and the KH broadcast observer can be
    // registered. Registering khBroadcastService as a KhObserver makes state pushes engine-driven:
    // initialize() fires gameStarted, each action fires gameUpdated (via GameEngine#notifyUpdated),
    // and resolution fires gameOver — all broadcast per-seat.
    final GameEngine.Builder builder =
        new GameEngine.Builder()
            .withId(entity.getId())
            .withState(state)
            .addListener(khBroadcastService)
            .addLifecycleObserver(new GameLifecycleListener<>());
    // Seat i = i-th withPlayer call; seat 0 is the creator. Bot seats bind their strategy driver
    // right here (mirrors Hearts' addPlayerObserver(player, strategy)), so seat and strategy can
    // never drift apart. The engine auto-drives strategy seats after every phase seed so the
    // readiness barrier never stalls on a bot; bots go through the same seat-aware actions as
    // humans — only the trigger differs.
    for (final UserEntity user : seatedUsers) {
      if (user.isBot()) {
        builder.withPlayer(user.toPlayer(), new KhBeamSearchStrategy());
      } else {
        builder.withPlayer(user.toPlayer());
      }
    }
    final GameEngine engine = builder.build();
    // Delegate initialization to the engine — it owns the knowledge of how a game starts.
    engine.initialize();
    return engine;
  }

  public Optional<UserEntity> findUser(final UUID id) {
    return usersRepository.findById(id);
  }

  /**
   * Inserts or incrementally updates the games-played summary for the given user and game type. If
   * no record exists yet, a new one is created. Otherwise the existing record's win/loss/draw
   * counts are incremented and {@code last_played_at} is refreshed.
   *
   * @param user The user who played.
   * @param gameType The type of game played.
   * @param wins Number of wins to add.
   * @param losses Number of losses to add.
   * @param draws Number of draws to add.
   * @return The updated (or newly created) entity.
   */
  @Transactional
  public UserGamesPlayedEntity updateGamesPlayed(
      final UserEntity user,
      final GameType gameType,
      final int wins,
      final int losses,
      final int draws) {
    final Optional<UserGamesPlayedEntity> existing =
        userGamesPlayedRepository.findByUserIdAndGameType(user.getId(), gameType);
    final UserGamesPlayedEntity entity;
    if (existing.isPresent()) {
      entity = existing.get();
      entity.setWins(entity.getWins() + wins);
      entity.setLosses(entity.getLosses() + losses);
      entity.setDraws(entity.getDraws() + draws);
      entity.setLastPlayedAt(new Date());
    } else {
      entity = new UserGamesPlayedEntity(user.getId(), gameType, wins, losses, draws, new Date());
    }
    return userGamesPlayedRepository.save(entity);
  }

  /**
   * Returns the games-played summary for the given user and game type, or {@code empty} if the user
   * has not yet played that type of game.
   *
   * @param user The user.
   * @param gameType The game type.
   * @return The summary, or {@code empty}.
   */
  public Optional<UserGamesPlayedEntity> getGamesPlayed(
      final UserEntity user, final GameType gameType) {
    return userGamesPlayedRepository.findByUserIdAndGameType(user.getId(), gameType);
  }

  /**
   * Returns all games-played summaries for the given user across every game type.
   *
   * @param user The user.
   * @return A list of summaries (may be empty if the user has never played).
   */
  public List<UserGamesPlayedEntity> getAllGamesPlayed(final UserEntity user) {
    return userGamesPlayedRepository.findByUserId(user.getId());
  }

  /**
   * Common {@link GameLifecycleObserver} registered against every game type's builder (GAME-117).
   * Keeps the {@link GameCache} in sync with each game's actual lifecycle — adding it the moment it
   * starts or resumes, and removing it the moment it ends (quit or naturally over) — rather than
   * relying solely on {@link GameCleanupService}'s periodic sweep. Also records games-played
   * statistics when a game ends naturally.
   *
   * <p>A new instance is created at each registration site (see the {@code build*Game} methods
   * below) rather than sharing one: {@link GameLifecycleObserver} is invariant in its type
   * parameter, so a single instance cannot simultaneously satisfy {@code
   * GameLifecycleObserver<XoGame>}, {@code GameLifecycleObserver<HeartsGame>}, and {@code
   * GameLifecycleObserver<GameEngine>}.
   *
   * @param <T> The underlying game implementation type.
   */
  private class GameLifecycleListener<T> implements GameLifecycleObserver<T> {

    @Override
    public void gameStarted(final Game<T> game) {
      gameCache.addGame(game);
    }

    @Override
    public void gameResumed(final Game<T> game) {
      gameCache.addGame(game);
    }

    @Override
    public void gameOver(final Game<T> game) {
      recordGamesPlayed(game);
      // Stamp overAt in the DB so the finished game leaves the player's active-games list.
      // XO/Hearts
      // set this via their persistence services; KH has none, so without this a completed King's
      // Hand match (e.g. when the rival kingdom falls) would linger as "active".
      markGameOver(game.getId());
      gameCache.removeGame(game);
    }

    @Override
    public void gameQuit(final Game<T> game) {
      // Quitting isn't a counted result, so games-played stats are intentionally left untouched.
      gameCache.removeGame(game);
    }
  }

  /**
   * Stamps {@code overAt} on the game's persisted entity so it is treated as complete (and drops
   * out of the active-games list / is eligible for cleanup). Idempotent: no-ops if the game is
   * already marked complete.
   *
   * @param gameId the id of the game that just ended.
   */
  private void markGameOver(final UUID gameId) {
    gamesRepository
        .findById(gameId)
        .filter(entity -> !entity.isComplete())
        .ifPresent(
            entity -> {
              entity.setOverAt(new Date());
              gamesRepository.save(entity);
            });
  }

  /**
   * Records games-played statistics (win/loss/draw) for every non-bot player in the provided game,
   * dispatching to the correct game-type-specific outcome logic.
   *
   * @param game The game that just ended naturally.
   */
  private void recordGamesPlayed(final Game<?> game) {
    switch (game.getGameType()) {
      case XO -> recordXoGamesPlayed((XoGame) game.getGame());
      case HEARTS -> recordHeartsGamesPlayed((HeartsGame) game.getGame());
      case KH -> recordKhGamesPlayed((GameEngine) game.getGame());
    }
  }

  private void recordXoGamesPlayed(final XoGame game) {
    final Optional<Line> winningLine = game.getWinner();
    for (final Player player : game.getPlayers()) {
      findUser(player.id())
          .filter(user -> !user.isBot())
          .ifPresent(
              user -> {
                if (winningLine.isEmpty()) {
                  updateGamesPlayed(user, GameType.XO, 0, 0, 1);
                } else if (winningLine.get().piece().equals(game.getPiece(player))) {
                  updateGamesPlayed(user, GameType.XO, 1, 0, 0);
                } else {
                  updateGamesPlayed(user, GameType.XO, 0, 1, 0);
                }
              });
    }
  }

  private void recordHeartsGamesPlayed(final HeartsGame game) {
    final HeartsState state = game.getGameState();
    final String winner = state.getWinner();
    final List<String> tied = state.getTied();
    for (final Player player : game.getPlayers()) {
      findUser(player.id())
          .filter(user -> !user.isBot())
          .ifPresent(
              user -> {
                if (player.name().equals(winner)) {
                  updateGamesPlayed(user, GameType.HEARTS, 1, 0, 0);
                } else if (tied.contains(player.name())) {
                  updateGamesPlayed(user, GameType.HEARTS, 0, 0, 1);
                } else {
                  updateGamesPlayed(user, GameType.HEARTS, 0, 1, 0);
                }
              });
    }
  }

  /**
   * Records KH games-played outcomes from the match result: {@code winnerSeat()} decides win vs
   * loss for each human seat. Covers every {@code gameOver} path — victory (all win-condition Great
   * Works completed), starvation knock-out, and forfeit (last kingdom standing wins). A {@code
   * winnerSeat} of {@code -1} (solo knock-out) records a loss.
   */
  private void recordKhGamesPlayed(final GameEngine game) {
    final int winnerSeat = game.getState().winnerSeat();
    for (final Player player : game.getPlayers()) {
      final int seat = game.seatOf(player.name());
      findUser(player.id())
          .filter(user -> !user.isBot())
          .ifPresent(
              user -> {
                if (seat >= 0 && seat == winnerSeat) {
                  updateGamesPlayed(user, GameType.KH, 1, 0, 0);
                } else {
                  updateGamesPlayed(user, GameType.KH, 0, 1, 0);
                }
              });
    }
  }

  protected <T extends GameEntity> Optional<T> findGameEntity(final UUID id, final Class<T> clazz) {
    final Optional<GameEntity> gameEntity = gamesRepository.findById(id);
    if (gameEntity.isPresent()) {
      if (clazz.isAssignableFrom(gameEntity.get().getClass())) {
        return Optional.of(clazz.cast(gameEntity.get()));
      }
    }
    return Optional.empty();
  }

  // TODO: should synchronize on smaller scope
  protected synchronized XoGame buildXoGame(final XoGameEntity gameEntity) {
    log.debug("Building XO game from entity: {}", gameEntity.getId());

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
    builder.addLifecycleObserver(new GameLifecycleListener<>());
    for (final UserEntity user : gameEntity.getPlayers()) {
      if (user.isBot()) {
        builder.addListener(
            new LookAheadStrategy(user.toPlayer(), playerGamePieceMap.get(user.toPlayer())));
      }
    }
    return builder.build();
  }
}
