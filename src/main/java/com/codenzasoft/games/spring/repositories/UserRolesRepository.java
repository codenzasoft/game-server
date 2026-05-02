package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.spring.entities.RoleEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRolesRepository extends CrudRepository<RoleEntity, UUID> {
  List<RoleEntity> findByUserId(UUID userId);
}
