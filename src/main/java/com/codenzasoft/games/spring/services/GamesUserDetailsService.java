package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import java.util.ArrayList;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class GamesUserDetailsService implements UserDetailsService {
  private static final Logger log = LoggerFactory.getLogger(GamesUserDetailsService.class);

  @Autowired private UsersRepository usersRepository;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    log.info("Loading user {}", username);
    final Optional<UserEntity> user = getUserByUsernameOrEmail(username);
    if (user.isEmpty()) {
      throw new UsernameNotFoundException("User not found");
    } else {
      log.info("User {} found", username);
    }
    return new org.springframework.security.core.userdetails.User(
        user.get().getUsername(), user.get().getPassword(), new ArrayList<>());
  }

  public Optional<UserEntity> getUserByUsernameOrEmail(final String name) {
    UserEntity user = usersRepository.findByUserName(name);
    if (user == null) {
      user = usersRepository.findByEmail(name);
    }
    return Optional.ofNullable(user);
  }
}
