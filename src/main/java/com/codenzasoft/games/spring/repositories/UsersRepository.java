package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.UserEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsersRepository extends JpaRepository<UserEntity, UUID> {
  UserEntity findByEmail(final String email);

  UserEntity findByUserName(final String userName);

  List<UserEntity> findByRole(Role role);
}
