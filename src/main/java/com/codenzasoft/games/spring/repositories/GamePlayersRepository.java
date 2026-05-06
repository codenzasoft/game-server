package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.spring.entities.GamePlayerEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GamePlayersRepository extends PagingAndSortingRepository<GamePlayerEntity, UUID> {
  public List<GamePlayerEntity> findByUserIdOrderByGameCreatedAtDesc(
      final UUID userId, final Pageable pageable);

  public List<GamePlayerEntity>
      findByUserIdAndGameOverAtIsNullAndGameQuitAtIsNullOrderByGameCreatedAtDesc(
          final UUID userId, final Pageable pageable);

  public List<GamePlayerEntity> findByGameId(final UUID gameId);
}
