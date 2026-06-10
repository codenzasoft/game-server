package com.codenzasoft.games.spring.repositories.hearts;

import com.codenzasoft.games.spring.entities.hearts.HeartsRoundHandEntity;
import com.codenzasoft.games.spring.entities.hearts.HeartsRoundHandId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HeartsRoundHandRepository
    extends CrudRepository<HeartsRoundHandEntity, HeartsRoundHandId> {

  List<HeartsRoundHandEntity> findByGameIdAndRoundNumber(UUID gameId, int roundNumber);

  boolean existsByGameId(UUID gameId);

  @Query("SELECT MAX(h.roundNumber) FROM HEARTS_ROUND_HANDS h WHERE h.gameId = :gameId")
  int findMaxRoundNumberByGameId(@Param("gameId") UUID gameId);
}
