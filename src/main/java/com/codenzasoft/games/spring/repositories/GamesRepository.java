package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.spring.entities.GameEntity;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GamesRepository extends CrudRepository<GameEntity, UUID> {}
