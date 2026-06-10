package com.codenzasoft.games.spring.repositories.hearts;

import com.codenzasoft.games.spring.entities.hearts.HeartsTrickCardEntity;
import com.codenzasoft.games.spring.entities.hearts.HeartsTrickCardId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HeartsTrickCardRepository
    extends CrudRepository<HeartsTrickCardEntity, HeartsTrickCardId> {

  List<HeartsTrickCardEntity> findByGameIdAndRoundNumberOrderByTrickNumberAscPlayOrderAsc(
      UUID gameId, int roundNumber);
}
