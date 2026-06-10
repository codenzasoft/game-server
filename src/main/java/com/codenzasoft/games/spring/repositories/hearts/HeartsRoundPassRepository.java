package com.codenzasoft.games.spring.repositories.hearts;

import com.codenzasoft.games.spring.entities.hearts.HeartsRoundPassEntity;
import com.codenzasoft.games.spring.entities.hearts.HeartsRoundPassId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HeartsRoundPassRepository
    extends CrudRepository<HeartsRoundPassEntity, HeartsRoundPassId> {

  List<HeartsRoundPassEntity> findByGameIdAndRoundNumber(UUID gameId, int roundNumber);
}
