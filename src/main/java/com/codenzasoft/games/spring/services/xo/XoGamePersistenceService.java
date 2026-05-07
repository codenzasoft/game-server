package com.codenzasoft.games.spring.services.xo;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.xo.XoGameEntity;
import com.codenzasoft.games.spring.entities.xo.XoGameMoveEntity;
import com.codenzasoft.games.spring.entities.xo.XoPieceEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import com.codenzasoft.games.spring.repositories.xando.XoGameRepository;
import com.codenzasoft.games.xo.*;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Persists {@link XoGame} state by registering as a {@link XoGameListener} for each game. */
@Service
public class XoGamePersistenceService implements XoGameListener {

  private static final Logger log = LoggerFactory.getLogger(XoGamePersistenceService.class);

  @Autowired private XoGameRepository xoGameRepository;
  @Autowired private UsersRepository usersRepository;
  @Autowired private EntityManager entityManager;

  @Override
  @Transactional
  public void gameStarted(final XoGame game, final XoState state) {
    final Optional<XoGameEntity> optional = xoGameRepository.findById(game.getId());
    if (optional.isPresent()) {
      // already created
      log.info("XO Game entity already exists: {}", game);
      return;
    }

    final XoGameEntity entity = new XoGameEntity(game.getId());
    final Map<Player, UserEntity> playerMap =
        game.getPlayers().stream()
            .collect(
                Collectors.toMap(
                    player -> player, player -> usersRepository.findById(player.id()).get()));
    final List<UserEntity> userEntities = game.getPlayers().stream().map(playerMap::get).toList();
    entity.setPlayers(userEntities);

    final List<XoPieceEntity> pieces =
        game.getPlayers().stream()
            .map(player -> new XoPieceEntity(game.getId(), player.id(), game.getPiece(player)))
            .toList();
    entity.setGamePieces(pieces);

    xoGameRepository.save(entity);
    entityManager.flush();
    log.info("XO Game entity created: {}", entity);
  }

  @Override
  @Transactional
  public void gameChanged(final XoGame game, final XoState state) {
    final Optional<XoGameEntity> optional = xoGameRepository.findById(game.getId());
    if (optional.isPresent()) {
      final XoGameEntity entity = optional.get();
      updateLastMove(entity, game, state);
      xoGameRepository.save(entity);
      entityManager.flush();
    }
  }

  private void updateLastMove(final XoGameEntity entity, final XoGame game, final XoState state) {
    final int numMoves = state.board().values().stream().mapToInt(List::size).sum();
    if (numMoves > 0) {
      final Piece lastPiece;
      if (numMoves % 2 == 0) {
        lastPiece = state.leader().opposite();
      } else {
        lastPiece = state.leader();
      }
      final List<Position> positions = state.board().get(lastPiece);
      final Player lastPlayer = game.getPlayer(lastPiece);
      final Position lastPosition = positions.get(positions.size() - 1);
      entity.addGameMove(
          new XoGameMoveEntity(
              entity.getId(),
              lastPlayer.id(),
              numMoves,
              lastPosition.row(),
              lastPosition.column()));
    }
  }

  @Override
  @Transactional
  public void gameOver(final XoGame game, final XoState state) {
    final Optional<XoGameEntity> optional = xoGameRepository.findById(game.getId());
    if (optional.isPresent()) {
      final XoGameEntity entity = optional.get();
      entity.setOverAt(new Date());
      updateLastMove(entity, game, state);
      xoGameRepository.save(entity);
      entityManager.flush();
    }
  }

  @Override
  @Transactional
  public void gameQuit(XoGame game, XoState state) {
    final Optional<XoGameEntity> optional = xoGameRepository.findById(game.getId());
    if (optional.isPresent()) {
      final XoGameEntity entity = optional.get();
      if (!entity.isComplete()) {
        entity.setQuitAt(new Date());
        xoGameRepository.save(entity);
        entityManager.flush();
      }
    }
  }
}
