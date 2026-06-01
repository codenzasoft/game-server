package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.spring.entities.FeedbackEntity;
import com.codenzasoft.games.spring.entities.FeedbackEntity.Status;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<FeedbackEntity, UUID> {

  Page<FeedbackEntity> findByStatus(Status status, Pageable pageable);
}
