package com.codenzasoft.games.spring.repositories.hearts;

import com.codenzasoft.games.spring.entities.hearts.HeartsGameEntity;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HeartsGameRepository extends CrudRepository<HeartsGameEntity, UUID> {}
