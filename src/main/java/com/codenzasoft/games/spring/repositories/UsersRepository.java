package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.spring.entities.UserEntity;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsersRepository extends CrudRepository<UserEntity, UUID> {
  UserEntity findByEmail(final String email);

  UserEntity findByUserName(final String userName);
}
