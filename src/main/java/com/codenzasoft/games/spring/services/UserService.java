package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.spring.config.Role;
import com.codenzasoft.games.spring.entities.RoleEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

  @Autowired UsersRepository usersRepository;

  @Transactional
  public UserEntity createBot(final String botName) {
    final UserEntity userEntity = new UserEntity();
    userEntity.setUserName(botName);
    final UserEntity saved = usersRepository.save(userEntity);
    saved.getRoles().add(new RoleEntity(saved.getId(), Role.BOT));
    return usersRepository.save(saved);
  }
}
