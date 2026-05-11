package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.spring.entities.GameEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GamesRepository extends CrudRepository<GameEntity, UUID> {

  @Query("SELECT g.id FROM GAMES g WHERE g.overAt IS NOT NULL OR g.quitAt IS NOT NULL")
  List<UUID> findCompletedGameIds();

  @Modifying
  @Query(
      value = "DELETE FROM games WHERE over_at IS NOT NULL OR quit_at IS NOT NULL",
      nativeQuery = true)
  int deleteCompletedGames();
}
