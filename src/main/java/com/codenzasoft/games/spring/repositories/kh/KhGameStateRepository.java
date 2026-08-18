package com.codenzasoft.games.spring.repositories.kh;

import com.codenzasoft.games.spring.entities.kh.KhGameStateEntity;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KhGameStateRepository extends CrudRepository<KhGameStateEntity, UUID> {}
