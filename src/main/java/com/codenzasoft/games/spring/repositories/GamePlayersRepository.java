package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.spring.entities.GamePlayerEntity;
import com.codenzasoft.games.spring.entities.GameStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GamePlayersRepository extends PagingAndSortingRepository<GamePlayerEntity, UUID> {
  public List<GamePlayerEntity> findByUserIdOrderByGameCreatedAtDesc(
      final UUID userId, final Pageable pageable);

  public List<GamePlayerEntity>
      findByUserIdAndGameOverAtIsNullAndGameQuitAtIsNullOrderByGameCreatedAtDesc(
          final UUID userId, final Pageable pageable);

  public List<GamePlayerEntity> findByGameId(final UUID gameId);

  @Query(
      "SELECT gp FROM GAME_PLAYERS gp"
          + " WHERE gp.game.createdBy.id = :userId AND gp.game.status = :status")
  List<GamePlayerEntity> findOutgoingByCreatorAndStatus(
      @Param("userId") UUID userId, @Param("status") GameStatus status);

  @Query(
      "SELECT gp FROM GAME_PLAYERS gp"
          + " WHERE gp.userId = :userId AND gp.game.status = :status"
          + " AND gp.game.createdBy.id != :userId")
  List<GamePlayerEntity> findIncomingByUserAndStatus(
      @Param("userId") UUID userId, @Param("status") GameStatus status);

  @Query(
      "SELECT gp FROM GAME_PLAYERS gp"
          + " WHERE gp.userId = :userId AND gp.game.status = 'ACTIVE'"
          + " AND gp.game.overAt IS NULL AND gp.game.quitAt IS NULL"
          + " ORDER BY gp.game.createdAt DESC")
  List<GamePlayerEntity> findActiveGamesByUser(@Param("userId") UUID userId);
}
