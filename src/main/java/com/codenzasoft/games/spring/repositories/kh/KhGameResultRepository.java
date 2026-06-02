package com.codenzasoft.games.spring.repositories.kh;

import com.codenzasoft.games.spring.entities.kh.KhGameResultEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KhGameResultRepository extends JpaRepository<KhGameResultEntity, UUID> {

  /** Top 5 results ordered by how long the player survived (year then season). */
  List<KhGameResultEntity> findTop5ByOrderByYearSurvivedDescSeasonOrderDesc();

  /** Top 5 results ordered by the highest population the player achieved. */
  List<KhGameResultEntity> findTop5ByOrderByPeakPopulationDesc();
}
