package com.codenzasoft.games.spring.repositories.xando;

import com.codenzasoft.games.spring.entities.xo.XoGameEntity;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface XoGameRepository extends CrudRepository<XoGameEntity, UUID> {}
