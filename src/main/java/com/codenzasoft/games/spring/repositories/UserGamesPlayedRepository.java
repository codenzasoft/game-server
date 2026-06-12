package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.spring.entities.UserGamesPlayedEntity;
import com.codenzasoft.games.spring.entities.ids.UserGamesPlayedId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserGamesPlayedRepository
    extends CrudRepository<UserGamesPlayedEntity, UserGamesPlayedId> {

  Optional<UserGamesPlayedEntity> findByUserIdAndGameType(UUID userId, GameType gameType);

  List<UserGamesPlayedEntity> findByUserId(UUID userId);
}
